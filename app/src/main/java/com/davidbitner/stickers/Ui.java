package com.davidbitner.stickers;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;

final class Ui {
    static final int BG = 0xFF08080C;
    static final int SURFACE = 0xFF111118;
    static final int SURFACE_HI = 0xFF1A1A23;
    static final int STROKE = 0xFF272732;
    static final int TEXT = 0xFFF2F2F7;
    static final int TEXT_DIM = 0xFF8D8DA1;
    static final int ACCENT = 0xFFC8FF3D;
    static final int CYAN = 0xFF38BDF8;
    static final int DANGER = 0xFFFB7185;
    static final int OK = 0xFF34D399;
    static final int WARN = 0xFFFBBF24;

    private Ui() {
    }

    static int dp(Context c, int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, c.getResources().getDisplayMetrics()));
    }

    static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    static TextView text(Context c, CharSequence value, float sp, int color, Typeface face) {
        TextView tv = new TextView(c);
        tv.setText(value);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        tv.setTypeface(face);
        tv.setIncludeFontPadding(false);
        return tv;
    }

    static TextView heading(Context c, CharSequence value, float sp) {
        return text(c, value, sp, TEXT, Typeface.create("sans-serif-black", Typeface.NORMAL));
    }

    static TextView title(Context c, CharSequence value, float sp) {
        return text(c, value, sp, TEXT, Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    static TextView body(Context c, CharSequence value, float sp) {
        return text(c, value, sp, TEXT_DIM, Typeface.create("sans-serif", Typeface.NORMAL));
    }

    static TextView mono(Context c, CharSequence value, float sp, int color) {
        TextView tv = text(c, value, sp, color, Typeface.MONOSPACE);
        tv.setLetterSpacing(0.06f);
        return tv;
    }

    static GradientDrawable box(Context c, int fill, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        if (strokeDp > 0) {
            d.setStroke(dp(c, strokeDp), strokeColor);
        }
        return d;
    }

    static GradientDrawable dashed(Context c, int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.TRANSPARENT);
        d.setCornerRadius(dp(c, radiusDp));
        d.setStroke(dp(c, 1), color, dp(c, 6), dp(c, 5));
        return d;
    }

    static Drawable ripple(Context c, Drawable content, int radiusDp) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(c, radiusDp));
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), content, mask);
    }

    static void enable(View v, boolean on) {
        v.setEnabled(on);
        v.setAlpha(on ? 1f : 0.35f);
    }

    static TextView primaryButton(Context c, String label) {
        TextView b = mono(c, label, 13, BG);
        b.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 20), dp(c, 17), dp(c, 20), dp(c, 17));
        GradientDrawable fill = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFFD6FF63, 0xFFB4EA2C});
        fill.setCornerRadius(dp(c, 16));
        b.setBackground(ripple(c, fill, 16));
        b.setClickable(true);
        return b;
    }

    static TextView secondaryButton(Context c, String label) {
        TextView b = mono(c, label, 13, TEXT);
        b.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 20), dp(c, 17), dp(c, 20), dp(c, 17));
        b.setBackground(ripple(c, box(c, SURFACE_HI, 16, STROKE, 1), 16));
        b.setClickable(true);
        return b;
    }

    static TextView dangerButton(Context c, String label) {
        TextView b = mono(c, label, 13, DANGER);
        b.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 20), dp(c, 17), dp(c, 20), dp(c, 17));
        b.setBackground(ripple(c, box(c, withAlpha(DANGER, 0x22), 16, withAlpha(DANGER, 0x66), 1), 16));
        b.setClickable(true);
        return b;
    }

    static TextView chip(Context c, String label, int color) {
        TextView t = mono(c, label, 10, color);
        t.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        t.setPadding(dp(c, 8), dp(c, 4), dp(c, 8), dp(c, 4));
        t.setBackground(box(c, withAlpha(color, 0x1F), 8, withAlpha(color, 0x55), 1));
        return t;
    }

    static void roundClip(View v, final int radiusPx) {
        v.setClipToOutline(true);
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        });
    }

    static LinearLayout.LayoutParams wide(Context c, int topDp, int bottomDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(c, topDp), 0, dp(c, bottomDp));
        return lp;
    }

    static LinearLayout.LayoutParams weighted(Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(c, 4), 0, dp(c, 4), 0);
        return lp;
    }

    static FrameLayout thumbTile(Context c, int sizeDp, int radiusDp, File file) {
        FrameLayout tile = new FrameLayout(c);
        tile.setBackground(new Checker(dp(c, 8)));
        roundClip(tile, dp(c, radiusDp));
        ImageView image = new ImageView(c);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tile.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        tile.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        Thumbs.into(image, file);
        return tile;
    }

    static Dialog dialog(Activity a, View body) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout holder = new FrameLayout(a);
        holder.setPadding(dp(a, 20), 0, dp(a, 20), 0);
        body.setBackground(box(a, SURFACE, 24, STROKE, 1));
        holder.addView(body, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        d.setContentView(holder);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.75f);
        }
        return d;
    }

    static void confirm(Activity a, String heading, String message, String positive,
                        boolean danger, final Runnable onYes) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(a, 22), dp(a, 24), dp(a, 22), dp(a, 18));
        box.addView(title(a, heading, 20));
        TextView msg = body(a, message, 14);
        msg.setLineSpacing(0f, 1.25f);
        msg.setPadding(0, dp(a, 10), 0, dp(a, 22));
        box.addView(msg);

        final Dialog d = dialog(a, box);
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = secondaryButton(a, "CANCELAR");
        cancel.setOnClickListener(v -> d.dismiss());
        TextView ok = danger ? dangerButton(a, positive) : primaryButton(a, positive);
        ok.setOnClickListener(v -> {
            d.dismiss();
            onYes.run();
        });
        row.addView(cancel, weighted(a));
        row.addView(ok, weighted(a));
        box.addView(row);
        d.show();
    }

    static final class SquareFrame extends FrameLayout {
        SquareFrame(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, widthMeasureSpec);
        }
    }

    static final class Checker extends Drawable {
        private final Paint paint = new Paint();
        private final int cell;

        Checker(int cellPx) {
            this.cell = Math.max(2, cellPx);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            paint.setColor(0xFF14141B);
            canvas.drawRect(b, paint);
            paint.setColor(0xFF1C1C25);
            int rows = (b.height() + cell - 1) / cell;
            int cols = (b.width() + cell - 1) / cell;
            for (int r = 0; r < rows; r++) {
                for (int col = 0; col < cols; col++) {
                    if (((r + col) & 1) == 0) {
                        int left = b.left + col * cell;
                        int top = b.top + r * cell;
                        canvas.drawRect(left, top,
                                Math.min(left + cell, b.right), Math.min(top + cell, b.bottom), paint);
                    }
                }
            }
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }
}
