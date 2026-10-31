package com.davidbitner.stickers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class PackStore {
    static final String PUBLISHER = "Figurinhas";
    private static final String META = "meta.json";
    private static final int TRAY_SIZE = 96;

    private PackStore() {
    }

    static File root(Context c) {
        File f = new File(c.getFilesDir(), "packs");
        if (!f.exists()) {
            f.mkdirs();
        }
        return f;
    }

    static File dir(Context c, String id) {
        return new File(root(c), id);
    }

    static File file(Context c, Pack p, String name) {
        return new File(dir(c, p.id), name);
    }

    static synchronized List<Pack> loadAll(Context c) {
        List<Pack> out = new ArrayList<>();
        File[] dirs = root(c).listFiles();
        if (dirs == null) {
            return out;
        }
        for (File d : dirs) {
            Pack p = read(d);
            if (p != null) {
                out.add(p);
            }
        }
        Collections.sort(out, (a, b) -> a.id.compareTo(b.id));
        return out;
    }

    static synchronized Pack load(Context c, String id) {
        if (id == null || id.contains("..") || id.contains("/")) {
            return null;
        }
        return read(dir(c, id));
    }

    static synchronized Pack create(Context c, String name, boolean animated) throws IOException {
        Pack p = new Pack("p" + System.currentTimeMillis(), name, animated);
        dir(c, p.id).mkdirs();
        save(c, p);
        return p;
    }

    static synchronized void save(Context c, Pack p) throws IOException {
        p.version++;
        JSONObject o = new JSONObject();
        try {
            o.put("id", p.id);
            o.put("name", p.name);
            o.put("animated", p.animated);
            o.put("version", p.version);
            JSONArray arr = new JSONArray();
            for (Pack.Item it : p.items) {
                JSONObject s = new JSONObject();
                s.put("file", it.file);
                s.put("emoji", it.emoji);
                arr.put(s);
            }
            o.put("stickers", arr);
        } catch (JSONException e) {
            throw new IOException(e);
        }
        File d = dir(c, p.id);
        d.mkdirs();
        File tmp = new File(d, META + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(o.toString().getBytes(StandardCharsets.UTF_8));
        }
        if (!tmp.renameTo(new File(d, META))) {
            throw new IOException("Não consegui salvar o pacote");
        }
        notifyChanged(c);
    }

    static synchronized void delete(Context c, Pack p) {
        deleteRecursive(dir(c, p.id));
        notifyChanged(c);
    }

    static void updateTray(Context c, Pack p) throws IOException {
        File tray = file(c, p, Pack.TRAY_FILE);
        if (p.items.isEmpty()) {
            tray.delete();
            return;
        }
        Bitmap src = BitmapFactory.decodeFile(file(c, p, p.items.get(0).file).getAbsolutePath());
        if (src == null) {
            src = Bitmap.createBitmap(TRAY_SIZE, TRAY_SIZE, Bitmap.Config.ARGB_8888);
            src.eraseColor(0xFF4F46E5);
        }
        Bitmap scaled = Bitmap.createScaledBitmap(src, TRAY_SIZE, TRAY_SIZE, true);
        try (FileOutputStream out = new FileOutputStream(tray)) {
            if (!scaled.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw new IOException("Não consegui gerar o ícone do pacote");
            }
        }
    }

    static void notifyChanged(Context c) {
        c.getContentResolver().notifyChange(StickerContentProvider.AUTHORITY_URI, null);
    }

    private static Pack read(File d) {
        File m = new File(d, META);
        if (!m.isFile()) {
            return null;
        }
        try (FileInputStream in = new FileInputStream(m); ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[4096];
            int n;
            while ((n = in.read(chunk)) != -1) {
                buf.write(chunk, 0, n);
            }
            JSONObject o = new JSONObject(new String(buf.toByteArray(), StandardCharsets.UTF_8));
            Pack p = new Pack(o.getString("id"), o.getString("name"), o.getBoolean("animated"));
            p.version = o.optLong("version", 1);
            JSONArray arr = o.optJSONArray("stickers");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject s = arr.getJSONObject(i);
                    p.items.add(new Pack.Item(s.getString("file"), s.optString("emoji", "😀")));
                }
            }
            return p;
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    private static void deleteRecursive(File f) {
        File[] children = f.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursive(child);
            }
        }
        f.delete();
    }
}
