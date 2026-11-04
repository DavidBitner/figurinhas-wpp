package com.davidbitner.stickers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

final class CropView extends View {
    private static final int NONE = 0;
    private static final int TL = 1;
    private static final int TR = 2;
    private static final int BL = 3;
    private static final int BR = 4;
    private static final int EDGE_L = 5;
    private static final int EDGE_T = 6;
    private static final int EDGE_R = 7;
    private static final int EDGE_B = 8;
    private static final int MOVE = 9;

    private final Ui.Checker checker;
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint dimPaint = new Paint();
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint();
    private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF image = new RectF();
    private final RectF crop = new RectF();
    private final RectF norm = new RectF(0f, 0f, 1f, 1f);
    private final float pad;
    private final float touch;
    private final float minSize;
    private final float cornerLen;

    private Bitmap source;
    private Bitmap display;
    private int turns;
    private boolean flip;
    private boolean square;
    private int mode = NONE;
    private float lastX;
    private float lastY;
    private Runnable listener;

    CropView(Context context) {
        super(context);
        pad = Ui.dp(context, 18);
        touch = Ui.dp(context, 30);
        minSize = Ui.dp(context, 56);
        cornerLen = Ui.dp(context, 20);
        checker = new Ui.Checker(Ui.dp(context, 10));
        dimPaint.setColor(0xCC08080C);
        borderPaint.setColor(Ui.ACCENT);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(Ui.dp(context, 1));
        gridPaint.setColor(0x40FFFFFF);
        gridPaint.setStrokeWidth(1f);
        cornerPaint.setColor(Ui.ACCENT);
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(Ui.dp(context, 4));
        cornerPaint.setStrokeCap(Paint.Cap.SQUARE);
    }

    void setOnChange(Runnable listener) {
        this.listener = listener;
    }

    void setSource(Bitmap bitmap) {
        source = bitmap;
        turns = 0;
        flip = false;
        norm.set(0f, 0f, 1f, 1f);
        rebuild();
    }

    void clear() {
        source = null;
        display = null;
        invalidate();
    }

    boolean hasImage() {
        return display != null;
    }

    boolean isSquare() {
        return square;
    }

    void setSquare(boolean value) {
        square = value;
        if (square && display != null) {
            enforceSquare();
        }
        changed();
    }

    void rotate() {
        if (source == null) {
            return;
        }
        syncNorm();
        float l = 1f - norm.bottom;
        float t = norm.left;
        float r = 1f - norm.top;
        float b = norm.right;
        norm.set(l, t, r, b);
        turns = (turns + 1) % 4;
        rebuild();
        if (square) {
            enforceSquare();
        }
        changed();
    }

    void mirror() {
        if (source == null) {
            return;
        }
        syncNorm();
        float l = 1f - norm.right;
        float r = 1f - norm.left;
        norm.set(l, norm.top, r, norm.bottom);
        turns = (4 - turns) % 4;
        flip = !flip;
        rebuild();
        changed();
    }

    void reset() {
        if (source == null) {
            return;
        }
        turns = 0;
        flip = false;
        norm.set(0f, 0f, 1f, 1f);
        rebuild();
        if (square) {
            enforceSquare();
        }
        changed();
    }

    Edit buildEdit() {
        syncNorm();
        return new Edit(turns, flip, norm.left, norm.top, norm.right, norm.bottom);
    }

    int[] cropPixels() {
        if (display == null) {
            return new int[]{0, 0};
        }
        syncNorm();
        return new int[]{
                Math.max(1, Math.round((norm.right - norm.left) * display.getWidth())),
                Math.max(1, Math.round((norm.bottom - norm.top) * display.getHeight()))};
    }

    private void rebuild() {
        display = Edit.transform(source, turns, flip);
        layoutImage();
        invalidate();
    }

    private void layoutImage() {
        if (display == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        float availW = getWidth() - pad * 2;
        float availH = getHeight() - pad * 2;
        float scale = Math.min(availW / display.getWidth(), availH / display.getHeight());
        float w = display.getWidth() * scale;
        float h = display.getHeight() * scale;
        float left = (getWidth() - w) / 2f;
        float top = (getHeight() - h) / 2f;
        image.set(left, top, left + w, top + h);
        checker.setBounds(Math.round(image.left), Math.round(image.top),
                Math.round(image.right), Math.round(image.bottom));
        crop.set(image.left + norm.left * w, image.top + norm.top * h,
                image.left + norm.right * w, image.top + norm.bottom * h);
    }

    private void syncNorm() {
        if (image.width() <= 0f || image.height() <= 0f) {
            return;
        }
        norm.set(clamp01((crop.left - image.left) / image.width()),
                clamp01((crop.top - image.top) / image.height()),
                clamp01((crop.right - image.left) / image.width()),
                clamp01((crop.bottom - image.top) / image.height()));
    }

    private void enforceSquare() {
        float s = Math.min(crop.width(), crop.height());
        float cx = crop.centerX();
        float cy = crop.centerY();
        crop.set(cx - s / 2f, cy - s / 2f, cx + s / 2f, cy + s / 2f);
        syncNorm();
    }

    private void changed() {
        invalidate();
        if (listener != null) {
            listener.run();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutImage();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (display == null || image.width() <= 0f) {
            return;
        }
        checker.draw(canvas);
        canvas.drawBitmap(display, null, image, bitmapPaint);

        canvas.drawRect(image.left, image.top, image.right, crop.top, dimPaint);
        canvas.drawRect(image.left, crop.bottom, image.right, image.bottom, dimPaint);
        canvas.drawRect(image.left, crop.top, crop.left, crop.bottom, dimPaint);
        canvas.drawRect(crop.right, crop.top, image.right, crop.bottom, dimPaint);

        float thirdW = crop.width() / 3f;
        float thirdH = crop.height() / 3f;
        for (int i = 1; i <= 2; i++) {
            canvas.drawLine(crop.left + thirdW * i, crop.top, crop.left + thirdW * i, crop.bottom, gridPaint);
            canvas.drawLine(crop.left, crop.top + thirdH * i, crop.right, crop.top + thirdH * i, gridPaint);
        }
        canvas.drawRect(crop, borderPaint);

        float len = Math.min(cornerLen, Math.min(crop.width(), crop.height()) / 2f);
        canvas.drawLine(crop.left, crop.top, crop.left + len, crop.top, cornerPaint);
        canvas.drawLine(crop.left, crop.top, crop.left, crop.top + len, cornerPaint);
        canvas.drawLine(crop.right, crop.top, crop.right - len, crop.top, cornerPaint);
        canvas.drawLine(crop.right, crop.top, crop.right, crop.top + len, cornerPaint);
        canvas.drawLine(crop.left, crop.bottom, crop.left + len, crop.bottom, cornerPaint);
        canvas.drawLine(crop.left, crop.bottom, crop.left, crop.bottom - len, cornerPaint);
        canvas.drawLine(crop.right, crop.bottom, crop.right - len, crop.bottom, cornerPaint);
        canvas.drawLine(crop.right, crop.bottom, crop.right, crop.bottom - len, cornerPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (display == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mode = hit(event.getX(), event.getY());
                if (mode == NONE) {
                    return false;
                }
                lastX = event.getX();
                lastY = event.getY();
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mode != NONE) {
                    drag(event.getX() - lastX, event.getY() - lastY);
                    lastX = event.getX();
                    lastY = event.getY();
                    changed();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mode != NONE) {
                    syncNorm();
                    mode = NONE;
                    changed();
                }
                return true;
            default:
                return true;
        }
    }

    private int hit(float x, float y) {
        float[][] corners = {
                {crop.left, crop.top}, {crop.right, crop.top},
                {crop.left, crop.bottom}, {crop.right, crop.bottom}};
        int[] ids = {TL, TR, BL, BR};
        int best = NONE;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i < corners.length; i++) {
            float dx = x - corners[i][0];
            float dy = y - corners[i][1];
            float dist = (float) Math.hypot(dx, dy);
            if (dist <= touch && dist < bestDist) {
                best = ids[i];
                bestDist = dist;
            }
        }
        if (best != NONE) {
            return best;
        }
        if (!square) {
            boolean withinX = x >= crop.left && x <= crop.right;
            boolean withinY = y >= crop.top && y <= crop.bottom;
            if (withinY && Math.abs(x - crop.left) <= touch / 2f) return EDGE_L;
            if (withinY && Math.abs(x - crop.right) <= touch / 2f) return EDGE_R;
            if (withinX && Math.abs(y - crop.top) <= touch / 2f) return EDGE_T;
            if (withinX && Math.abs(y - crop.bottom) <= touch / 2f) return EDGE_B;
        }
        if (crop.contains(x, y)) {
            return MOVE;
        }
        return NONE;
    }

    private void drag(float dx, float dy) {
        switch (mode) {
            case MOVE: {
                float w = crop.width();
                float h = crop.height();
                float left = clamp(crop.left + dx, image.left, image.right - w);
                float top = clamp(crop.top + dy, image.top, image.bottom - h);
                crop.set(left, top, left + w, top + h);
                break;
            }
            case EDGE_L:
                crop.left = clamp(crop.left + dx, image.left, crop.right - minSize);
                break;
            case EDGE_R:
                crop.right = clamp(crop.right + dx, crop.left + minSize, image.right);
                break;
            case EDGE_T:
                crop.top = clamp(crop.top + dy, image.top, crop.bottom - minSize);
                break;
            case EDGE_B:
                crop.bottom = clamp(crop.bottom + dy, crop.top + minSize, image.bottom);
                break;
            default:
                dragCorner(dx, dy);
                break;
        }
    }

    private void dragCorner(float dx, float dy) {
        boolean movesLeft = mode == TL || mode == BL;
        boolean movesTop = mode == TL || mode == TR;
        if (!square) {
            if (movesLeft) {
                crop.left = clamp(crop.left + dx, image.left, crop.right - minSize);
            } else {
                crop.right = clamp(crop.right + dx, crop.left + minSize, image.right);
            }
            if (movesTop) {
                crop.top = clamp(crop.top + dy, image.top, crop.bottom - minSize);
            } else {
                crop.bottom = clamp(crop.bottom + dy, crop.top + minSize, image.bottom);
            }
            return;
        }
        float anchorX = movesLeft ? crop.right : crop.left;
        float anchorY = movesTop ? crop.bottom : crop.top;
        float cornerX = (movesLeft ? crop.left : crop.right) + dx;
        float cornerY = (movesTop ? crop.top : crop.bottom) + dy;
        float signX = movesLeft ? -1f : 1f;
        float signY = movesTop ? -1f : 1f;
        float w = Math.max(0f, (cornerX - anchorX) * signX);
        float h = Math.max(0f, (cornerY - anchorY) * signY);
        float room = Math.min(
                signX > 0 ? image.right - anchorX : anchorX - image.left,
                signY > 0 ? image.bottom - anchorY : anchorY - image.top);
        float size = Math.min(Math.max(Math.max(w, h), minSize), room);
        float x2 = anchorX + signX * size;
        float y2 = anchorY + signY * size;
        crop.set(Math.min(anchorX, x2), Math.min(anchorY, y2), Math.max(anchorX, x2), Math.max(anchorY, y2));
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
