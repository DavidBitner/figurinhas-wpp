package com.davidbitner.stickers;

import android.graphics.Bitmap;
import android.graphics.Matrix;

final class Edit {
    final int turns;
    final boolean flip;
    final float left;
    final float top;
    final float right;
    final float bottom;

    Edit(int turns, boolean flip, float left, float top, float right, float bottom) {
        this.turns = ((turns % 4) + 4) % 4;
        this.flip = flip;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    boolean isIdentity() {
        return turns == 0 && !flip && left <= 0.001f && top <= 0.001f && right >= 0.999f && bottom >= 0.999f;
    }

    Bitmap apply(Bitmap src) {
        if (isIdentity()) {
            return src;
        }
        Bitmap oriented = transform(src, turns, flip);
        int w = oriented.getWidth();
        int h = oriented.getHeight();
        int x = clamp(Math.round(left * w), 0, w - 1);
        int y = clamp(Math.round(top * h), 0, h - 1);
        int cw = clamp(Math.round(right * w) - x, 1, w - x);
        int ch = clamp(Math.round(bottom * h) - y, 1, h - y);
        if (x == 0 && y == 0 && cw == w && ch == h) {
            return oriented;
        }
        return Bitmap.createBitmap(oriented, x, y, cw, ch);
    }

    static Bitmap transform(Bitmap src, int turns, boolean flip) {
        if (turns == 0 && !flip) {
            return src;
        }
        Matrix m = new Matrix();
        if (flip) {
            m.postScale(-1f, 1f);
        }
        if (turns != 0) {
            m.postRotate(90f * turns);
        }
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
