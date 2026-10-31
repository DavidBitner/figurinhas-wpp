package com.davidbitner.stickers;

import android.content.Context;
import android.net.Uri;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

final class Importer {
    static final String STATIC_NAME = "Estáticas";
    static final String ANIMATED_NAME = "Animadas";
    private static final String DEFAULT_EMOJI = "😀";

    interface Listener {
        void onProgress(int done, int total);
    }

    static final class Result {
        int staticAdded;
        int animatedAdded;
        final List<String> problems = new ArrayList<>();
    }

    private Importer() {
    }

    static boolean isAnimated(Context c, Uri uri) {
        String type = c.getContentResolver().getType(uri);
        if (type != null) {
            type = type.toLowerCase(Locale.ROOT);
            if (type.startsWith("video/") || type.equals("image/gif")) {
                return true;
            }
            if (type.startsWith("image/")) {
                return false;
            }
        }
        String path = uri.getLastPathSegment();
        if (path == null) {
            return false;
        }
        path = path.toLowerCase(Locale.ROOT);
        return path.endsWith(".gif") || path.endsWith(".mp4") || path.endsWith(".mov")
                || path.endsWith(".webm") || path.endsWith(".mkv") || path.endsWith(".3gp");
    }

    static Result run(Context c, List<Uri> uris, boolean removeBackground, Listener listener) {
        Result result = new Result();
        List<Pack> packs = new ArrayList<>(PackStore.loadAll(c));
        Set<Pack> touched = new LinkedHashSet<>();
        Set<Pack> created = new LinkedHashSet<>();

        for (int i = 0; i < uris.size(); i++) {
            listener.onProgress(i, uris.size());
            Uri uri = uris.get(i);
            boolean animated = isAnimated(c, uri);
            try {
                StickerProcessor.Output out = animated
                        ? StickerProcessor.processAnimated(c, uri)
                        : StickerProcessor.processStatic(c, uri, removeBackground);
                Pack target = target(c, packs, created, animated);
                String name = "s" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + ".webp";
                try (FileOutputStream fos = new FileOutputStream(PackStore.file(c, target, name))) {
                    fos.write(out.data);
                }
                target.items.add(new Pack.Item(name, DEFAULT_EMOJI));
                touched.add(target);
                if (animated) {
                    result.animatedAdded++;
                } else {
                    result.staticAdded++;
                }
                if (out.warning != null) {
                    result.problems.add("ARQUIVO " + (i + 1) + ": " + out.warning);
                }
            } catch (Exception | OutOfMemoryError e) {
                String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                result.problems.add("ARQUIVO " + (i + 1) + ": " + message);
            }
        }

        for (Pack pack : touched) {
            try {
                PackStore.updateTray(c, pack);
                PackStore.save(c, pack);
            } catch (IOException e) {
                result.problems.add("FALHA AO SALVAR " + pack.name + ": " + e.getMessage());
            }
        }
        for (Pack pack : created) {
            if (pack.items.isEmpty()) {
                PackStore.delete(c, pack);
            }
        }
        listener.onProgress(uris.size(), uris.size());
        return result;
    }

    private static Pack target(Context c, List<Pack> packs, Set<Pack> created, boolean animated)
            throws IOException {
        String base = animated ? ANIMATED_NAME : STATIC_NAME;
        int existing = 0;
        for (Pack p : packs) {
            if (p.animated == animated && p.name.startsWith(base)) {
                existing++;
                if (p.items.size() < Pack.MAX_STICKERS) {
                    return p;
                }
            }
        }
        Pack pack = PackStore.create(c, existing == 0 ? base : base + " " + (existing + 1), animated);
        packs.add(pack);
        created.add(pack);
        return pack;
    }
}
