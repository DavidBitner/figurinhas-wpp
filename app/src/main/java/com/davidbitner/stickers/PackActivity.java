package com.davidbitner.stickers;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PackActivity extends Activity {
    static final String EXTRA_ID = "id";
    private static final int REQUEST_PICK = 100;
    private static final String DEFAULT_EMOJI = "😀";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private Pack pack;
    private boolean busy;
    private StickerAdapter adapter;
    private TextView meta;
    private TextView hint;
    private TextView sendButton;
    private TextView statusText;
    private View statusCard;
    private ProgressBar progress;
    private Switch removeBackground;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pack = PackStore.load(this, getIntent().getStringExtra(EXTRA_ID));
        if (pack == null) {
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        root.addView(buildHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams optionsParams = Ui.wide(this, 0, 14);
        optionsParams.leftMargin = Ui.dp(this, 22);
        optionsParams.rightMargin = Ui.dp(this, 22);
        root.addView(buildOptions(), optionsParams);

        adapter = new StickerAdapter();
        GridView grid = new GridView(this);
        grid.setNumColumns(3);
        grid.setHorizontalSpacing(Ui.dp(this, 10));
        grid.setVerticalSpacing(Ui.dp(this, 10));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT));
        grid.setVerticalScrollBarEnabled(false);
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        grid.setClipToPadding(false);
        grid.setPadding(Ui.dp(this, 22), Ui.dp(this, 2), Ui.dp(this, 22), Ui.dp(this, 14));
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> {
            if (!busy && adapter.indexOf(position) < 0) {
                pick();
            }
        });
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            int index = adapter.indexOf(position);
            if (busy || index < 0) {
                return false;
            }
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            confirmRemoveSticker(index);
            return true;
        });
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(buildStatus(), statusParams());

        View divider = new View(this);
        divider.setBackgroundColor(Ui.STROKE);
        root.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        root.addView(buildBottomBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        setStatus("");
        refresh();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        worker.shutdown();
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(this, 16), Ui.dp(this, 22), Ui.dp(this, 22), Ui.dp(this, 16));

        TextView back = Ui.mono(this, "←", 20, Ui.TEXT);
        back.setGravity(Gravity.CENTER);
        back.setBackground(Ui.ripple(this, Ui.box(this, Ui.SURFACE_HI, 14, Ui.STROKE, 1), 14));
        back.setClickable(true);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 8), 0);
        TextView name = Ui.heading(this, pack.name, 24);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        meta = Ui.mono(this, "", 11, Ui.TEXT_DIM);
        meta.setPadding(0, Ui.dp(this, 6), 0, 0);
        titles.addView(name);
        titles.addView(meta);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView delete = Ui.mono(this, "EXCLUIR", 11, Ui.DANGER);
        delete.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        delete.setGravity(Gravity.CENTER);
        delete.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        delete.setBackground(Ui.ripple(this,
                Ui.box(this, Ui.withAlpha(Ui.DANGER, 0x14), 12, Ui.withAlpha(Ui.DANGER, 0x44), 1), 12));
        delete.setClickable(true);
        delete.setOnClickListener(v -> confirmDeletePack());
        header.addView(delete, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return header;
    }

    private View buildOptions() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
        card.setBackground(Ui.box(this, Ui.SURFACE, 18, Ui.STROKE, 1));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        if (pack.animated) {
            texts.addView(Ui.title(this, "Pacote animado", 15));
            TextView info = Ui.mono(this, "GIF OU VÍDEO  ·  ATÉ 10 S  ·  ≤ 500 KB", 10, Ui.CYAN);
            info.setPadding(0, Ui.dp(this, 6), 0, 0);
            texts.addView(info);
            card.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            return card;
        }

        texts.addView(Ui.title(this, "Remover fundo", 15));
        TextView sub = Ui.mono(this, "IA NO APARELHO  ·  RECORTA O OBJETO", 10, Ui.TEXT_DIM);
        sub.setPadding(0, Ui.dp(this, 6), 0, 0);
        texts.addView(sub);
        card.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        removeBackground = new Switch(this);
        removeBackground.setChecked(true);
        int[][] states = {{android.R.attr.state_checked}, {}};
        removeBackground.setThumbTintList(new ColorStateList(states, new int[]{Ui.ACCENT, Ui.TEXT_DIM}));
        removeBackground.setTrackTintList(new ColorStateList(states, new int[]{Ui.withAlpha(Ui.ACCENT, 0x66), Ui.STROKE}));
        card.addView(removeBackground, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private View buildStatus() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        card.setBackground(Ui.box(this, Ui.SURFACE, 16, Ui.STROKE, 1));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setIndeterminate(false);
        progress.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.STROKE));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 4));
        barParams.bottomMargin = Ui.dp(this, 10);
        card.addView(progress, barParams);

        statusText = Ui.mono(this, "", 12, Ui.TEXT_DIM);
        statusText.setLineSpacing(0f, 1.25f);
        card.addView(statusText);
        statusCard = card;
        return card;
    }

    private LinearLayout.LayoutParams statusParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(this, 22), 0, Ui.dp(this, 22), Ui.dp(this, 14));
        return lp;
    }

    private View buildBottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(Ui.dp(this, 22), Ui.dp(this, 14), Ui.dp(this, 22), Ui.dp(this, 20));

        hint = Ui.mono(this, "", 11, Ui.TEXT_DIM);
        bar.addView(hint, Ui.wide(this, 0, 12));

        sendButton = Ui.primaryButton(this, "ADICIONAR AO WHATSAPP");
        sendButton.setOnClickListener(v -> WhatsApp.addPack(this, pack.id, pack.name));
        bar.addView(sendButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return bar;
    }

    private void refresh() {
        int count = pack.items.size();
        meta.setText(count + " / " + Pack.MAX_STICKERS + "  ·  " + (pack.animated ? "ANIMADO" : "ESTÁTICO"));
        Ui.enable(sendButton, !busy && pack.isReady());
        int missing = Pack.MIN_STICKERS - count;
        if (busy) {
            hint.setText("PROCESSANDO…");
            hint.setTextColor(Ui.TEXT_DIM);
        } else if (missing > 0) {
            hint.setText("FALTAM " + missing + (missing == 1 ? " FIGURINHA" : " FIGURINHAS") + " PARA ENVIAR");
            hint.setTextColor(Ui.WARN);
        } else {
            hint.setText("●  PRONTO PARA ENVIAR");
            hint.setTextColor(Ui.OK);
        }
        adapter.notifyDataSetChanged();
    }

    private void setStatus(CharSequence text) {
        statusText.setText(text);
        statusText.setVisibility(text.length() == 0 ? View.GONE : View.VISIBLE);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        statusCard.setVisibility(busy || text.length() > 0 ? View.VISIBLE : View.GONE);
    }

    private void pick() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                pack.animated ? new String[]{"image/gif", "video/*"} : new String[]{"image/*"});
        startActivityForResult(intent, REQUEST_PICK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK && resultCode == RESULT_OK && data != null) {
            List<Uri> uris = new ArrayList<>();
            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) {
                    uris.add(clip.getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            if (!uris.isEmpty() && !busy) {
                importUris(uris);
            }
        } else if (requestCode == WhatsApp.REQUEST_ADD && resultCode == RESULT_CANCELED && data != null) {
            String error = data.getStringExtra("validation_error");
            if (error != null) {
                Toast.makeText(this, "O WhatsApp recusou o pacote: " + error, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void importUris(final List<Uri> uris) {
        busy = true;
        progress.setProgress(0);
        setStatus("");
        refresh();
        final Pack work = pack.copy();
        final boolean cut = removeBackground != null && removeBackground.isChecked() && !pack.animated;
        worker.execute(() -> {
            int added = 0;
            List<String> problems = new ArrayList<>();
            for (int i = 0; i < uris.size(); i++) {
                final Uri uri = uris.get(i);
                final int number = i + 1;
                if (work.items.size() >= Pack.MAX_STICKERS) {
                    problems.add("LIMITE DE " + Pack.MAX_STICKERS + " FIGURINHAS ATINGIDO");
                    break;
                }
                final int percent = i * 100 / uris.size();
                final String label = "PROCESSANDO " + number + " / " + uris.size() + "…";
                runOnUiThread(() -> {
                    progress.setProgress(percent);
                    setStatus(label);
                });
                try {
                    StickerProcessor.Output out = work.animated
                            ? StickerProcessor.processAnimated(this, uri)
                            : StickerProcessor.processStatic(this, uri, cut);
                    String name = "s" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + ".webp";
                    try (FileOutputStream fos = new FileOutputStream(PackStore.file(this, work, name))) {
                        fos.write(out.data);
                    }
                    work.items.add(new Pack.Item(name, DEFAULT_EMOJI));
                    added++;
                    if (out.warning != null) {
                        problems.add("ARQUIVO " + number + ": " + out.warning);
                    }
                } catch (Exception | OutOfMemoryError e) {
                    problems.add("ARQUIVO " + number + ": " + describe(e));
                }
            }
            if (added > 0) {
                try {
                    PackStore.updateTray(this, work);
                    PackStore.save(this, work);
                } catch (IOException e) {
                    problems.add("FALHA AO SALVAR: " + describe(e));
                }
            }
            final int addedFinal = added;
            runOnUiThread(() -> finishImport(work, addedFinal, problems));
        });
    }

    private void finishImport(Pack work, int added, List<String> problems) {
        if (isDestroyed()) {
            return;
        }
        pack = work;
        busy = false;
        progress.setProgress(100);
        SpannableStringBuilder summary = new SpannableStringBuilder();
        append(summary, (added > 0 ? "✓ " : "✗ ") + added
                + (added == 1 ? " FIGURINHA ADICIONADA" : " FIGURINHAS ADICIONADAS"), added > 0 ? Ui.OK : Ui.DANGER);
        for (String problem : problems) {
            summary.append('\n');
            append(summary, "! " + problem, Ui.WARN);
        }
        setStatus(summary);
        refresh();
    }

    private static void append(SpannableStringBuilder sb, String value, int color) {
        int start = sb.length();
        sb.append(value);
        sb.setSpan(new ForegroundColorSpan(color), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static String describe(Throwable e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private void confirmRemoveSticker(final int index) {
        Ui.confirm(this, "Remover figurinha", "Esta figurinha será removida do pacote.", "REMOVER", true,
                () -> removeSticker(index));
    }

    private void removeSticker(int index) {
        if (busy || index < 0 || index >= pack.items.size()) {
            return;
        }
        Pack.Item item = pack.items.remove(index);
        PackStore.file(this, pack, item.file).delete();
        try {
            PackStore.updateTray(this, pack);
            PackStore.save(this, pack);
        } catch (IOException e) {
            Toast.makeText(this, "Falha ao salvar: " + describe(e), Toast.LENGTH_LONG).show();
        }
        setStatus("");
        refresh();
    }

    private void confirmDeletePack() {
        if (busy) {
            return;
        }
        Ui.confirm(this, "Excluir pacote",
                "O pacote \"" + pack.name + "\" será apagado do app. Figurinhas já adicionadas ao WhatsApp continuam lá.",
                "EXCLUIR", true, () -> {
                    PackStore.delete(this, pack);
                    finish();
                });
    }

    private final class StickerAdapter extends BaseAdapter {
        private boolean showAdd() {
            return !busy && pack.items.size() < Pack.MAX_STICKERS;
        }

        int indexOf(int position) {
            return showAdd() ? position - 1 : position;
        }

        @Override
        public int getCount() {
            return pack.items.size() + (showAdd() ? 1 : 0);
        }

        @Override
        public Object getItem(int position) {
            int index = indexOf(position);
            return index < 0 ? null : pack.items.get(index);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return indexOf(position) < 0 ? 0 : 1;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            int index = indexOf(position);
            if (index < 0) {
                return convertView != null ? convertView : addTile();
            }
            Ui.SquareFrame tile = convertView instanceof Ui.SquareFrame
                    ? (Ui.SquareFrame) convertView
                    : stickerTile();
            ImageView image = (ImageView) tile.getChildAt(0);
            TextView badge = (TextView) tile.getChildAt(1);
            TextView icon = (TextView) tile.getChildAt(2);
            badge.setText(String.format(Locale.US, "%02d", index + 1));
            icon.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
            Thumbs.into(image, PackStore.file(PackActivity.this, pack, pack.items.get(index).file));
            return tile;
        }

        private View addTile() {
            Ui.SquareFrame tile = new Ui.SquareFrame(PackActivity.this);
            tile.setBackground(Ui.ripple(PackActivity.this, Ui.dashed(PackActivity.this, Ui.withAlpha(Ui.ACCENT, 0x99), 14), 14));
            tile.setClickable(false);
            LinearLayout inner = new LinearLayout(PackActivity.this);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setGravity(Gravity.CENTER);
            TextView plus = Ui.text(PackActivity.this, "+", 30, Ui.ACCENT, Typeface.MONOSPACE);
            TextView label = Ui.mono(PackActivity.this, "ADICIONAR", 10, Ui.ACCENT);
            label.setPadding(0, Ui.dp(PackActivity.this, 6), 0, 0);
            inner.addView(plus);
            inner.addView(label);
            tile.addView(inner, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return tile;
        }

        private Ui.SquareFrame stickerTile() {
            Ui.SquareFrame tile = new Ui.SquareFrame(PackActivity.this);
            tile.setBackground(new Ui.Checker(Ui.dp(PackActivity.this, 9)));
            Ui.roundClip(tile, Ui.dp(PackActivity.this, 14));

            ImageView image = new ImageView(PackActivity.this);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setPadding(Ui.dp(PackActivity.this, 6), Ui.dp(PackActivity.this, 6),
                    Ui.dp(PackActivity.this, 6), Ui.dp(PackActivity.this, 6));
            tile.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            TextView badge = Ui.mono(PackActivity.this, "", 10, Ui.TEXT);
            badge.setPadding(Ui.dp(PackActivity.this, 6), Ui.dp(PackActivity.this, 3),
                    Ui.dp(PackActivity.this, 6), Ui.dp(PackActivity.this, 3));
            badge.setBackground(Ui.box(PackActivity.this, 0xB308080C, 7, 0, 0));
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.START);
            badgeParams.setMargins(Ui.dp(PackActivity.this, 6), 0, 0, Ui.dp(PackActivity.this, 6));
            tile.addView(badge, badgeParams);

            TextView icon = Ui.mono(PackActivity.this, "ÍCONE", 9, Ui.BG);
            icon.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            icon.setPadding(Ui.dp(PackActivity.this, 6), Ui.dp(PackActivity.this, 3),
                    Ui.dp(PackActivity.this, 6), Ui.dp(PackActivity.this, 3));
            icon.setBackground(Ui.box(PackActivity.this, Ui.ACCENT, 7, 0, 0));
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.START);
            iconParams.setMargins(Ui.dp(PackActivity.this, 6), Ui.dp(PackActivity.this, 6), 0, 0);
            tile.addView(icon, iconParams);
            return tile;
        }
    }
}
