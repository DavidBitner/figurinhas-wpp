package com.davidbitner.stickers;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int STRIP_TILES = 5;
    private static final int REQUEST_IMPORT = 200;
    private static final String PREFS = "settings";
    private static final String PREF_REMOVE_BG = "remove_bg";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Switch removeBackground;
    private boolean importing;
    private final List<Pack> packs = new ArrayList<>();
    private LinearLayout list;
    private TextView stats;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(Ui.dp(this, 22), Ui.dp(this, 28), Ui.dp(this, 22), Ui.dp(this, 14));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        GradientDrawable dotShape = new GradientDrawable();
        dotShape.setShape(GradientDrawable.OVAL);
        dotShape.setColor(Ui.ACCENT);
        dot.setBackground(dotShape);
        brand.addView(dot, new LinearLayout.LayoutParams(Ui.dp(this, 8), Ui.dp(this, 8)));
        TextView brandLabel = Ui.mono(this, "FIGURINHAS  v" + BuildConfig.VERSION_NAME, 11, Ui.ACCENT);
        brandLabel.setPadding(Ui.dp(this, 8), 0, 0, 0);
        brand.addView(brandLabel);

        TextView heading = Ui.heading(this, "Seus pacotes", 36);
        heading.setPadding(0, Ui.dp(this, 16), 0, 0);
        stats = Ui.mono(this, "", 11, Ui.TEXT_DIM);
        stats.setPadding(0, Ui.dp(this, 10), 0, 0);

        header.addView(brand);
        header.addView(heading);
        header.addView(stats);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.dp(this, 22), Ui.dp(this, 6), Ui.dp(this, 22), Ui.dp(this, 20));
        scroll.addView(list, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View divider = new View(this);
        divider.setBackgroundColor(Ui.STROKE);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(Ui.dp(this, 22), Ui.dp(this, 16), Ui.dp(this, 22), Ui.dp(this, 20));
        LinearLayout option = new LinearLayout(this);
        option.setOrientation(LinearLayout.HORIZONTAL);
        option.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout optionTexts = new LinearLayout(this);
        optionTexts.setOrientation(LinearLayout.VERTICAL);
        optionTexts.addView(Ui.title(this, "Remover fundo das fotos", 14));
        TextView optionHint = Ui.mono(this, "IA NO APARELHO  ·  SÓ ESTÁTICAS", 10, Ui.TEXT_DIM);
        optionHint.setPadding(0, Ui.dp(this, 5), 0, 0);
        optionTexts.addView(optionHint);
        option.addView(optionTexts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        removeBackground = new Switch(this);
        removeBackground.setChecked(prefs.getBoolean(PREF_REMOVE_BG, true));
        int[][] states = {{android.R.attr.state_checked}, {}};
        removeBackground.setThumbTintList(new ColorStateList(states, new int[]{Ui.ACCENT, Ui.TEXT_DIM}));
        removeBackground.setTrackTintList(new ColorStateList(states,
                new int[]{Ui.withAlpha(Ui.ACCENT, 0x66), Ui.STROKE}));
        removeBackground.setOnCheckedChangeListener((b, checked) ->
                prefs.edit().putBoolean(PREF_REMOVE_BG, checked).apply());
        option.addView(removeBackground);
        bar.addView(option, Ui.wide(this, 0, 14));

        TextView importButton = Ui.primaryButton(this, "IMPORTAR FIGURINHAS");
        importButton.setOnClickListener(v -> pickFiles());
        bar.addView(importButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView create = Ui.secondaryButton(this, "+  NOVO PACOTE MANUAL");
        create.setOnClickListener(v -> askNewPack());
        bar.addView(create, Ui.wide(this, 10, 0));

        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        packs.clear();
        packs.addAll(PackStore.loadAll(this));
        render();
    }

    private void render() {
        list.removeAllViews();
        int total = 0;
        for (Pack pack : packs) {
            total += pack.items.size();
        }
        if (packs.isEmpty()) {
            stats.setText("NADA POR AQUI AINDA");
            list.addView(emptyState());
            return;
        }
        stats.setText(packs.size() + (packs.size() == 1 ? " PACOTE" : " PACOTES")
                + "  ·  " + total + (total == 1 ? " FIGURINHA" : " FIGURINHAS"));
        for (Pack pack : packs) {
            list.addView(packCard(pack), Ui.wide(this, 0, 12));
        }
    }

    private View emptyState() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(Ui.dp(this, 12), Ui.dp(this, 56), Ui.dp(this, 12), 0);

        FrameLayout plus = new FrameLayout(this);
        plus.setBackground(Ui.dashed(this, Ui.withAlpha(Ui.ACCENT, 0x99), 22));
        TextView glyph = Ui.text(this, "+", 34, Ui.ACCENT, Typeface.MONOSPACE);
        glyph.setGravity(Gravity.CENTER);
        plus.addView(glyph, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        box.addView(plus, new LinearLayout.LayoutParams(Ui.dp(this, 84), Ui.dp(this, 84)));

        TextView title = Ui.title(this, "Comece importando", 20);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, Ui.dp(this, 24), 0, Ui.dp(this, 8));
        TextView hint = Ui.body(this, "Escolha fotos, GIFs e vídeos de uma vez. O app separa em pacotes estáticos e animados automaticamente.", 14);
        hint.setGravity(Gravity.CENTER);
        hint.setLineSpacing(0f, 1.25f);
        box.addView(title);
        box.addView(hint);
        return box;
    }

    private View packCard(final Pack pack) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18));
        card.setBackground(Ui.ripple(this, Ui.box(this, Ui.SURFACE, 22, Ui.STROKE, 1), 22));
        card.setClickable(true);
        card.setOnClickListener(v -> openPack(pack));

        TextView name = Ui.title(this, pack.name, 19);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(name);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        chips.addView(Ui.chip(this, pack.animated ? "ANIMADO" : "ESTÁTICO", pack.animated ? Ui.CYAN : Ui.TEXT_DIM));
        int missing = Pack.MIN_STICKERS - pack.items.size();
        TextView status = missing > 0
                ? Ui.chip(this, "FALTAM " + missing, Ui.WARN)
                : Ui.chip(this, "PRONTO", Ui.OK);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.leftMargin = Ui.dp(this, 8);
        chips.addView(status, statusParams);
        TextView count = Ui.mono(this, pack.items.size() + " / " + Pack.MAX_STICKERS, 11, Ui.TEXT_DIM);
        count.setPadding(Ui.dp(this, 10), 0, 0, 0);
        chips.addView(count);
        card.addView(chips, Ui.wide(this, 12, 14));

        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        if (pack.items.isEmpty()) {
            TextView empty = Ui.mono(this, "VAZIO  ·  TOQUE PARA ADICIONAR", 11, Ui.TEXT_DIM);
            empty.setPadding(Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18));
            empty.setBackground(Ui.dashed(this, Ui.STROKE, 14));
            strip.addView(empty);
        } else {
            int shown = Math.min(STRIP_TILES, pack.items.size());
            for (int i = 0; i < shown; i++) {
                View tile = Ui.thumbTile(this, 52, 12, PackStore.file(this, pack, pack.items.get(i).file));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52));
                lp.rightMargin = Ui.dp(this, 8);
                strip.addView(tile, lp);
            }
            int extra = pack.items.size() - shown;
            if (extra > 0) {
                TextView more = Ui.mono(this, "+" + extra, 12, Ui.TEXT_DIM);
                more.setGravity(Gravity.CENTER);
                more.setBackground(Ui.box(this, Ui.SURFACE_HI, 12, Ui.STROKE, 1));
                strip.addView(more, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52)));
            }
        }
        card.addView(strip);
        return card;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        worker.shutdown();
    }

    private void pickFiles() {
        if (importing) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        startActivityForResult(intent, REQUEST_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMPORT || resultCode != RESULT_OK || data == null) {
            return;
        }
        List<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                uris.add(clip.getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (!uris.isEmpty()) {
            startImport(uris);
        }
    }

    private void startImport(final List<Uri> uris) {
        importing = true;
        final boolean cut = removeBackground.isChecked();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 22), Ui.dp(this, 24), Ui.dp(this, 22), Ui.dp(this, 22));
        box.addView(Ui.title(this, "Importando", 20));
        final ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(uris.size());
        progress.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.STROKE));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 4));
        barParams.topMargin = Ui.dp(this, 20);
        barParams.bottomMargin = Ui.dp(this, 12);
        box.addView(progress, barParams);
        final TextView status = Ui.mono(this, "PROCESSANDO 1 / " + uris.size() + "…", 12, Ui.TEXT_DIM);
        status.setLineSpacing(0f, 1.25f);
        box.addView(status);

        final Dialog dialog = Ui.dialog(this, box);
        dialog.setCancelable(false);
        dialog.show();

        worker.execute(() -> {
            final Importer.Result result = Importer.run(this, uris, cut, (done, total) -> runOnUiThread(() -> {
                progress.setProgress(done);
                status.setText("PROCESSANDO " + Math.min(done + 1, total) + " / " + total + "…");
            }));
            runOnUiThread(() -> finishImport(dialog, box, result));
        });
    }

    private void finishImport(Dialog dialog, LinearLayout box, Importer.Result result) {
        importing = false;
        if (isDestroyed()) {
            return;
        }
        box.removeAllViews();
        int total = result.staticAdded + result.animatedAdded;
        box.addView(Ui.title(this, total > 0 ? "Importação concluída" : "Nada foi importado", 20));

        SpannableStringBuilder summary = new SpannableStringBuilder();
        if (result.staticAdded > 0) {
            line(summary, "✓ " + result.staticAdded + " em \"" + Importer.STATIC_NAME + "\"", Ui.OK);
        }
        if (result.animatedAdded > 0) {
            line(summary, "✓ " + result.animatedAdded + " em \"" + Importer.ANIMATED_NAME + "\"", Ui.CYAN);
        }
        for (String problem : result.problems) {
            line(summary, "! " + problem, Ui.WARN);
        }
        TextView text = Ui.mono(this, summary, 12, Ui.TEXT_DIM);
        text.setLineSpacing(0f, 1.3f);
        text.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 22));
        box.addView(text);

        TextView ok = Ui.primaryButton(this, "OK");
        ok.setOnClickListener(v -> dialog.dismiss());
        box.addView(ok, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setCancelable(true);

        packs.clear();
        packs.addAll(PackStore.loadAll(this));
        render();
    }

    private static void line(SpannableStringBuilder sb, String value, int color) {
        if (sb.length() > 0) {
            sb.append('\n');
        }
        int start = sb.length();
        sb.append(value);
        sb.setSpan(new ForegroundColorSpan(color), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void openPack(Pack pack) {
        Intent intent = new Intent(this, PackActivity.class);
        intent.putExtra(PackActivity.EXTRA_ID, pack.id);
        startActivity(intent);
    }

    private void askNewPack() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 22), Ui.dp(this, 24), Ui.dp(this, 22), Ui.dp(this, 18));

        box.addView(Ui.title(this, "Novo pacote", 20));
        TextView nameLabel = Ui.mono(this, "NOME", 10, Ui.TEXT_DIM);
        nameLabel.setPadding(0, Ui.dp(this, 20), 0, Ui.dp(this, 8));
        box.addView(nameLabel);

        final EditText name = new EditText(this);
        name.setHint("Ex.: Memes do grupo");
        name.setHintTextColor(Ui.withAlpha(Ui.TEXT_DIM, 0xAA));
        name.setTextColor(Ui.TEXT);
        name.setTextSize(16);
        name.setSingleLine(true);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        name.setBackground(Ui.box(this, Ui.SURFACE_HI, 14, Ui.STROKE, 1));
        box.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView typeLabel = Ui.mono(this, "TIPO", 10, Ui.TEXT_DIM);
        typeLabel.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 8));
        box.addView(typeLabel);

        final boolean[] animated = {false};
        final TextView staticOption = Ui.mono(this, "ESTÁTICO", 12, Ui.TEXT);
        final TextView animatedOption = Ui.mono(this, "ANIMADO", 12, Ui.TEXT);
        final TextView explanation = Ui.body(this, "", 13);
        LinearLayout segments = new LinearLayout(this);
        segments.setOrientation(LinearLayout.HORIZONTAL);
        for (TextView option : new TextView[]{staticOption, animatedOption}) {
            option.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            option.setGravity(Gravity.CENTER);
            option.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
            option.setClickable(true);
        }
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        left.rightMargin = Ui.dp(this, 5);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        right.leftMargin = Ui.dp(this, 5);
        segments.addView(staticOption, left);
        segments.addView(animatedOption, right);
        box.addView(segments);
        explanation.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 22));
        explanation.setLineSpacing(0f, 1.2f);
        box.addView(explanation);

        final Runnable paint = () -> {
            styleSegment(staticOption, !animated[0], Ui.ACCENT);
            styleSegment(animatedOption, animated[0], Ui.CYAN);
            explanation.setText(animated[0]
                    ? "GIFs e vídeos curtos, até 10 s por figurinha. Sem remoção de fundo."
                    : "Fotos e imagens, com remoção de fundo automática.");
        };
        staticOption.setOnClickListener(v -> {
            animated[0] = false;
            paint.run();
        });
        animatedOption.setOnClickListener(v -> {
            animated[0] = true;
            paint.run();
        });
        paint.run();

        final Dialog dialog = Ui.dialog(this, box);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = Ui.secondaryButton(this, "CANCELAR");
        cancel.setOnClickListener(v -> dialog.dismiss());
        TextView create = Ui.primaryButton(this, "CRIAR");
        create.setOnClickListener(v -> {
            String value = name.getText().toString().trim();
            if (value.isEmpty()) {
                value = "Meu pacote";
            }
            if (value.length() > 128) {
                value = value.substring(0, 128);
            }
            try {
                Pack created = PackStore.create(this, value, animated[0]);
                dialog.dismiss();
                openPack(created);
            } catch (IOException e) {
                Toast.makeText(this, "Não consegui criar o pacote: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        row.addView(cancel, Ui.weighted(this));
        row.addView(create, Ui.weighted(this));
        box.addView(row);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
    }

    private void styleSegment(TextView option, boolean selected, int color) {
        if (selected) {
            option.setTextColor(color);
            option.setBackground(Ui.ripple(this,
                    Ui.box(this, Ui.withAlpha(color, 0x22), 14, Ui.withAlpha(color, 0xCC), 1), 14));
        } else {
            option.setTextColor(Ui.TEXT_DIM);
            option.setBackground(Ui.ripple(this, Ui.box(this, Ui.SURFACE_HI, 14, Ui.STROKE, 1), 14));
        }
    }
}
