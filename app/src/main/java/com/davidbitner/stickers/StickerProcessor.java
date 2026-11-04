package com.davidbitner.stickers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Movie;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

final class StickerProcessor {
    static final int SIZE = 512;

    private static final int STATIC_LIMIT = 98 * 1024;
    private static final int ANIMATED_FRAMES_LIMIT = 490 * 1024;
    private static final int ANIMATED_FILE_LIMIT = 498 * 1024;
    private static final int MAX_ANIMATION_MS = 9600;
    private static final int MAX_FRAMES = 90;
    private static final int DECODE_MIN_SIDE = 1024;

    private StickerProcessor() {
    }

    static final class Output {
        final byte[] data;
        final String warning;

        Output(byte[] data, String warning) {
            this.data = data;
            this.warning = warning;
        }
    }

    static Bitmap loadPreview(Context c, Uri uri) throws IOException {
        return decodeBitmap(c, uri);
    }

    static Output processStatic(Context c, Uri uri, boolean removeBackground) throws IOException {
        return processStatic(c, uri, removeBackground, null);
    }

    static Output processStatic(Context c, Uri uri, boolean removeBackground, Edit edit) throws IOException {
        Bitmap work = decodeBitmap(c, uri);
        if (edit != null) {
            work = edit.apply(work);
        }
        String warning = null;
        if (removeBackground) {
            try {
                work = trimTransparent(BackgroundRemover.remove(work));
            } catch (Exception e) {
                warning = "não consegui remover o fundo (precisa de internet na primeira vez)";
            }
        }
        return new Output(encodeStatic(fit(work)), warning);
    }

    static Output processAnimated(Context c, Uri uri) throws IOException {
        return processAnimated(c, uri, null);
    }

    static Output processAnimated(Context c, Uri uri, Edit edit) throws IOException {
        List<Bitmap> frames = new ArrayList<>();
        String type = c.getContentResolver().getType(uri);
        int stepMs;
        if (type != null && type.startsWith("video/")) {
            stepMs = extractVideoFrames(c, uri, frames, edit);
        } else {
            stepMs = extractGifFrames(c, uri, frames, edit);
        }
        return new Output(encodeAnimated(frames, stepMs), null);
    }

    private static Bitmap decodeBitmap(Context c, Uri uri) throws IOException {
        String type = c.getContentResolver().getType(uri);
        if (type != null && type.startsWith("video/")) {
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            try {
                r.setDataSource(c, uri);
                Bitmap frame = r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (frame == null) {
                    throw new IOException("Não consegui ler o vídeo");
                }
                return frame;
            } catch (RuntimeException e) {
                throw new IOException("Não consegui ler o vídeo", e);
            } finally {
                r.release();
            }
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = open(c, uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int longest = Math.max(bounds.outWidth, bounds.outHeight);
        if (longest <= 0) {
            throw new IOException("Imagem inválida");
        }
        int sample = 1;
        while (longest / (sample * 2) >= DECODE_MIN_SIDE) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap;
        try (InputStream in = open(c, uri)) {
            bitmap = BitmapFactory.decodeStream(in, null, options);
        }
        if (bitmap == null) {
            throw new IOException("Imagem inválida");
        }
        return applyOrientation(bitmap, readOrientation(c, uri));
    }

    private static int readOrientation(Context c, Uri uri) {
        try (InputStream in = open(c, uri)) {
            ExifInterface exif = new ExifInterface(in);
            return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception e) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    private static Bitmap applyOrientation(Bitmap b, int orientation) {
        Matrix m = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                m.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                m.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                m.postRotate(270);
                break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                m.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                m.postScale(1, -1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                m.postRotate(90);
                m.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                m.postRotate(270);
                m.postScale(-1, 1);
                break;
            default:
                return b;
        }
        return Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
    }

    private static Bitmap trimTransparent(Bitmap b) {
        int w = b.getWidth();
        int h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        int minX = w;
        int minY = h;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                if ((px[row + x] >>> 24) > 16) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < minX || maxY < minY) {
            return b;
        }
        return Bitmap.createBitmap(b, minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private static Bitmap fit(Bitmap src) {
        float scale = Math.min((float) SIZE / src.getWidth(), (float) SIZE / src.getHeight());
        int w = Math.max(1, Math.round(src.getWidth() * scale));
        int h = Math.max(1, Math.round(src.getHeight() * scale));
        Bitmap scaled = resizeSmooth(src, w, h);
        Bitmap out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawBitmap(scaled, (SIZE - w) / 2f, (SIZE - h) / 2f, null);
        return out;
    }

    private static Bitmap resizeSmooth(Bitmap src, int w, int h) {
        Bitmap current = src;
        while (current.getWidth() >= w * 2 && current.getHeight() >= h * 2) {
            current = Bitmap.createScaledBitmap(current, current.getWidth() / 2, current.getHeight() / 2, true);
        }
        if (current.getWidth() == w && current.getHeight() == h) {
            return current;
        }
        return Bitmap.createScaledBitmap(current, w, h, true);
    }

    private static byte[] encodeStatic(Bitmap b) throws IOException {
        int[] qualities = {92, 85, 78, 70, 60, 50, 40, 30};
        for (int q : qualities) {
            byte[] data = compress(b, q);
            if (data.length <= STATIC_LIMIT) {
                return data;
            }
        }
        throw new IOException("Imagem complexa demais para caber em 100 KB");
    }

    private static byte[] compress(Bitmap b, int quality) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Bitmap.CompressFormat format = Build.VERSION.SDK_INT >= 30
                ? Bitmap.CompressFormat.WEBP_LOSSY
                : Bitmap.CompressFormat.WEBP;
        if (!b.compress(format, quality, out)) {
            throw new IOException("Falha ao codificar WebP");
        }
        return out.toByteArray();
    }

    private static int pickStep(int totalMs) {
        int step = Math.max(50, Math.min(120, totalMs / 45));
        while ((totalMs + step - 1) / step > MAX_FRAMES) {
            step += 10;
        }
        return step;
    }

    private static int extractGifFrames(Context c, Uri uri, List<Bitmap> frames, Edit edit) throws IOException {
        byte[] data = readAll(c, uri);
        Movie movie = Movie.decodeByteArray(data, 0, data.length);
        if (movie == null || movie.width() <= 0 || movie.height() <= 0) {
            throw new IOException("O arquivo não é um GIF válido");
        }
        int duration = movie.duration();
        if (duration <= 0) {
            throw new IOException("O GIF não tem animação");
        }
        int total = Math.min(duration, MAX_ANIMATION_MS);
        int step = pickStep(total);
        if (edit != null && !edit.isIdentity()) {
            float s = Math.min(1f, (float) DECODE_MIN_SIDE / Math.max(movie.width(), movie.height()));
            int w = Math.max(1, Math.round(movie.width() * s));
            int h = Math.max(1, Math.round(movie.height() * s));
            for (int t = 0; t < total; t += step) {
                Bitmap full = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(full);
                canvas.scale(s, s);
                movie.setTime(t);
                movie.draw(canvas, 0f, 0f);
                frames.add(fit(edit.apply(full)));
            }
            if (frames.size() < 2) {
                throw new IOException("O GIF tem poucos quadros");
            }
            return step;
        }
        float scale = Math.min((float) SIZE / movie.width(), (float) SIZE / movie.height());
        float dx = (SIZE - movie.width() * scale) / 2f;
        float dy = (SIZE - movie.height() * scale) / 2f;
        for (int t = 0; t < total; t += step) {
            Bitmap frame = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(frame);
            canvas.translate(dx, dy);
            canvas.scale(scale, scale);
            movie.setTime(t);
            movie.draw(canvas, 0f, 0f);
            frames.add(frame);
        }
        if (frames.size() < 2) {
            throw new IOException("O GIF tem poucos quadros");
        }
        return step;
    }

    private static int extractVideoFrames(Context c, Uri uri, List<Bitmap> frames, Edit edit) throws IOException {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(c, uri);
            String durationText = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long duration = durationText == null ? 0 : Long.parseLong(durationText);
            if (duration <= 0) {
                throw new IOException("Não consegui ler a duração do vídeo");
            }
            int total = (int) Math.min(duration, MAX_ANIMATION_MS);
            int step = pickStep(total);
            for (int t = 0; t < total; t += step) {
                Bitmap source = r.getFrameAtTime(t * 1000L, MediaMetadataRetriever.OPTION_CLOSEST);
                if (source != null) {
                    frames.add(fit(edit != null ? edit.apply(source) : source));
                }
            }
            if (frames.size() < 2) {
                throw new IOException("O vídeo é curto demais");
            }
            return step;
        } catch (RuntimeException e) {
            throw new IOException("Não consegui ler o vídeo", e);
        } finally {
            r.release();
        }
    }

    private static byte[] encodeAnimated(List<Bitmap> frames, int stepMs) throws IOException {
        int[] qualities = {75, 60, 48, 38, 28, 20};
        for (int skip = 1; skip <= 3; skip++) {
            List<Bitmap> used = new ArrayList<>();
            for (int i = 0; i < frames.size(); i += skip) {
                used.add(frames.get(i));
            }
            if (used.size() < 2) {
                break;
            }
            for (int q : qualities) {
                List<byte[]> encoded = new ArrayList<>();
                long total = 0;
                for (Bitmap frame : used) {
                    byte[] data = compress(frame, q);
                    encoded.add(data);
                    total += data.length;
                    if (total > ANIMATED_FRAMES_LIMIT) {
                        break;
                    }
                }
                if (total > ANIMATED_FRAMES_LIMIT) {
                    continue;
                }
                byte[] result = AnimatedWebp.assemble(encoded, SIZE, SIZE, stepMs * skip);
                if (result.length <= ANIMATED_FILE_LIMIT) {
                    return result;
                }
            }
        }
        throw new IOException("Animação pesada demais para caber em 500 KB");
    }

    private static InputStream open(Context c, Uri uri) throws IOException {
        InputStream in = c.getContentResolver().openInputStream(uri);
        if (in == null) {
            throw new IOException("Não consegui abrir o arquivo");
        }
        return in;
    }

    private static byte[] readAll(Context c, Uri uri) throws IOException {
        try (InputStream in = open(c, uri); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16384];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }
}
