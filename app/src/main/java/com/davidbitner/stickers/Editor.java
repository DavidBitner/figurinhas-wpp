package com.davidbitner.stickers;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class Editor {
    interface Callback {
        void onDone(List<Edit> edits);

        void onCancel();
    }

    private final Activity activity;
    private final List<Uri> uris;
    private final Callback callback;
    private final Edit[] edits;
    private final ExecutorService loader = Executors.newSingleThreadExecutor();

    private Dialog dialog;
    private CropView crop;
    private TextView title;
    private TextView sizeLabel;
    private TextView shapeButton;
    private TextView skipButton;
    private TextView nextButton;
    private TextView rotateButton;
    private TextView mirrorButton;
    private TextView resetButton;
    private int index = -1;
    private boolean finished;

    private Editor(Activity activity, List<Uri> uris, Callback callback) {
        this.activity = activity;
        this.uris = uris;
        this.callback = callback;
        this.edits = new Edit[uris.size()];
    }

    static void start(Activity activity, List<Uri> uris, Callback callback) {
        new Editor(activity, uris, callback).open();
    }

    private void open() {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 16), Ui.dp(activity, 22), Ui.dp(activity, 6));
        TextView close = Ui.mono(activity, "✕", 18, Ui.TEXT);
        close.setGravity(Gravity.CENTER);
        close.setBackground(Ui.ripple(activity, Ui.box(activity, Ui.SURFACE_HI, 14, Ui.STROKE, 1), 14));
        close.setClickable(true);
        close.setOnClickListener(v -> dialog.cancel());
        top.addView(close, new LinearLayout.LayoutParams(Ui.dp(activity, 44), Ui.dp(activity, 44)));
        title = Ui.mono(activity, "", 12, Ui.ACCENT);
        title.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        title.setPadding(Ui.dp(activity, 14), 0, 0, 0);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        crop = new CropView(activity);
        crop.setOnChange(this::updateLabels);
        root.addView(crop, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        sizeLabel = Ui.mono(activity, "", 11, Ui.TEXT_DIM);
        sizeLabel.setGravity(Gravity.CENTER);
        sizeLabel.setPadding(0, Ui.dp(activity, 4), 0, Ui.dp(activity, 12));
        root.addView(sizeLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout tools = new LinearLayout(activity);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setPadding(Ui.dp(activity, 19), 0, Ui.dp(activity, 19), Ui.dp(activity, 14));
        rotateButton = tool("GIRAR");
        rotateButton.setOnClickListener(v -> crop.rotate());
        mirrorButton = tool("ESPELHAR");
        mirrorButton.setOnClickListener(v -> crop.mirror());
        shapeButton = tool("LIVRE");
        shapeButton.setOnClickListener(v -> crop.setSquare(!crop.isSquare()));
        resetButton = tool("REDEFINIR");
        resetButton.setOnClickListener(v -> crop.reset());
        for (TextView button : new TextView[]{rotateButton, mirrorButton, shapeButton, resetButton}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(Ui.dp(activity, 3), 0, Ui.dp(activity, 3), 0);
            tools.addView(button, lp);
        }
        root.addView(tools, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View divider = new View(activity);
        divider.setBackgroundColor(Ui.STROKE);
        root.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));

        LinearLayout bottom = new LinearLayout(activity);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setPadding(Ui.dp(activity, 18), Ui.dp(activity, 14), Ui.dp(activity, 18), Ui.dp(activity, 20));
        skipButton = Ui.secondaryButton(activity, "SEM EDITAR");
        skipButton.setOnClickListener(v -> {
            edits[index] = null;
            next();
        });
        nextButton = Ui.primaryButton(activity, "PRÓXIMA");
        nextButton.setOnClickListener(v -> {
            Edit edit = crop.buildEdit();
            edits[index] = edit.isIdentity() ? null : edit;
            next();
        });
        bottom.addView(skipButton, Ui.weighted(activity));
        bottom.addView(nextButton, Ui.weighted(activity));
        root.addView(bottom, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog = new Dialog(activity, android.R.style.Theme_Material_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(root);
        dialog.setOnCancelListener(d -> {
            if (!finished) {
                finished = true;
                loader.shutdownNow();
                callback.onCancel();
            }
        });
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Ui.BG));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setStatusBarColor(Ui.BG);
            window.setNavigationBarColor(Ui.BG);
        }
        dialog.show();
        show(0);
    }

    private void show(final int position) {
        index = position;
        crop.clear();
        setLoading(true);
        title.setText("EDITAR  " + (position + 1) + " / " + uris.size());
        nextButton.setText(position == uris.size() - 1 ? "CONCLUIR" : "PRÓXIMA");
        sizeLabel.setText("CARREGANDO…");
        loader.execute(() -> {
            Bitmap bitmap = null;
            try {
                bitmap = StickerProcessor.loadPreview(activity, uris.get(position));
            } catch (Exception | OutOfMemoryError ignored) {
            }
            final Bitmap result = bitmap;
            activity.runOnUiThread(() -> {
                if (finished || index != position) {
                    return;
                }
                if (result == null) {
                    edits[position] = null;
                    next();
                    return;
                }
                crop.setSource(result);
                setLoading(false);
                updateLabels();
            });
        });
    }

    private void next() {
        if (index + 1 < uris.size()) {
            show(index + 1);
            return;
        }
        finished = true;
        loader.shutdown();
        dialog.dismiss();
        callback.onDone(Arrays.asList(edits));
    }

    private void setLoading(boolean loading) {
        for (TextView button : new TextView[]{rotateButton, mirrorButton, shapeButton, resetButton, nextButton, skipButton}) {
            Ui.enable(button, !loading);
        }
    }

    private void updateLabels() {
        if (!crop.hasImage()) {
            return;
        }
        int[] size = crop.cropPixels();
        sizeLabel.setText("RECORTE  " + size[0] + " × " + size[1] + " PX");
        boolean square = crop.isSquare();
        shapeButton.setText(square ? "1:1" : "LIVRE");
        shapeButton.setTextColor(square ? Ui.ACCENT : Ui.TEXT);
        shapeButton.setBackground(Ui.ripple(activity, square
                ? Ui.box(activity, Ui.withAlpha(Ui.ACCENT, 0x22), 12, Ui.withAlpha(Ui.ACCENT, 0xCC), 1)
                : Ui.box(activity, Ui.SURFACE_HI, 12, Ui.STROKE, 1), 12));
    }

    private TextView tool(String label) {
        TextView t = Ui.mono(activity, label, 11, Ui.TEXT);
        t.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setPadding(0, Ui.dp(activity, 14), 0, Ui.dp(activity, 14));
        t.setBackground(Ui.ripple(activity, Ui.box(activity, Ui.SURFACE_HI, 12, Ui.STROKE, 1), 12));
        t.setClickable(true);
        return t;
    }
}
