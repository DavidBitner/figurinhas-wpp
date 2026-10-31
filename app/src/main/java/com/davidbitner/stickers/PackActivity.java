package com.davidbitner.stickers;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.LruCache;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PackActivity extends Activity {
    static final String EXTRA_ID = "id";
    private static final int REQUEST_PICK = 100;
    private static final String DEFAULT_EMOJI = "😀";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService thumbPool = Executors.newFixedThreadPool(2);
    private final LruCache<String, Bitmap> thumbs = new LruCache<>(60);

    private Pack pack;
    private boolean busy;
    private StickerAdapter adapter;
    private TextView subtitle;
    private TextView status;
    private CheckBox removeBackground;
    private Button addButton;
    private Button whatsappButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pack = PackStore.load(this, getIntent().getStringExtra(EXTRA_ID));
        if (pack == null) {
            finish();
            return;
        }

        int pad = Ui.dp(this, 16);
        int gap = Ui.dp(this, 8);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = Ui.text(this, pack.name, 24, true);
        subtitle = Ui.text(this, "", 14, false);
        subtitle.setAlpha(0.7f);
        subtitle.setPadding(0, 0, 0, gap);

        removeBackground = new CheckBox(this);
        removeBackground.setText("Remover o fundo automaticamente");
        removeBackground.setChecked(true);
        removeBackground.setVisibility(pack.animated ? View.GONE : View.VISIBLE);

        adapter = new StickerAdapter();
        GridView grid = new GridView(this);
        grid.setNumColumns(3);
        grid.setHorizontalSpacing(gap);
        grid.setVerticalSpacing(gap);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setAdapter(adapter);
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            confirmRemoveSticker(position);
            return true;
        });

        status = Ui.text(this, "", 13, false);
        status.setPadding(0, gap, 0, gap);

        addButton = new Button(this);
        addButton.setText(pack.animated ? "Adicionar GIFs/vídeos" : "Adicionar fotos");
        addButton.setOnClickListener(v -> pick());

        whatsappButton = new Button(this);
        whatsappButton.setText("Adicionar ao WhatsApp");
        whatsappButton.setOnClickListener(v -> WhatsApp.addPack(this, pack.id, pack.name));

        Button deleteButton = new Button(this);
        deleteButton.setText("Excluir pacote");
        deleteButton.setOnClickListener(v -> confirmDeletePack());

        LinearLayout.LayoutParams wide = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        root.addView(title, wide);
        root.addView(subtitle, wide);
        root.addView(removeBackground, wide);
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(status, wide);
        root.addView(addButton, wide);
        root.addView(whatsappButton, wide);
        root.addView(deleteButton, wide);
        setContentView(root);
        refresh();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        worker.shutdown();
        thumbPool.shutdown();
    }

    private void refresh() {
        subtitle.setText(pack.items.size() + " de " + Pack.MAX_STICKERS + " figurinhas "
                + (pack.animated ? "animadas" : "estáticas") + " · toque e segure para remover");
        addButton.setEnabled(!busy && pack.items.size() < Pack.MAX_STICKERS);
        whatsappButton.setEnabled(!busy && pack.isReady());
        if (!busy && !pack.isReady() && status.getText().length() == 0) {
            status.setText("Adicione pelo menos " + Pack.MIN_STICKERS + " figurinhas para enviar ao WhatsApp.");
        }
        adapter.notifyDataSetChanged();
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
        status.setText("");
        refresh();
        final Pack work = pack.copy();
        final boolean cut = removeBackground.isChecked() && !pack.animated;
        worker.execute(() -> {
            int added = 0;
            List<String> problems = new ArrayList<>();
            for (int i = 0; i < uris.size(); i++) {
                final Uri uri = uris.get(i);
                final int number = i + 1;
                if (work.items.size() >= Pack.MAX_STICKERS) {
                    problems.add("limite de " + Pack.MAX_STICKERS + " figurinhas atingido");
                    break;
                }
                final String progress = "Processando " + number + " de " + uris.size() + "…";
                runOnUiThread(() -> status.setText(progress));
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
                        problems.add("Arquivo " + number + ": " + out.warning);
                    }
                } catch (Exception | OutOfMemoryError e) {
                    problems.add("Arquivo " + number + ": " + describe(e));
                }
            }
            if (added > 0) {
                try {
                    PackStore.updateTray(this, work);
                    PackStore.save(this, work);
                } catch (IOException e) {
                    problems.add("Falha ao salvar: " + describe(e));
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
        StringBuilder message = new StringBuilder();
        message.append(added).append(added == 1 ? " figurinha adicionada." : " figurinhas adicionadas.");
        for (String problem : problems) {
            message.append('\n').append(problem);
        }
        status.setText(message.toString());
        refresh();
    }

    private static String describe(Throwable e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private void confirmRemoveSticker(final int position) {
        if (busy) {
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage("Remover esta figurinha?")
                .setPositiveButton("Remover", (dialog, which) -> removeSticker(position))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void removeSticker(int position) {
        if (position < 0 || position >= pack.items.size()) {
            return;
        }
        Pack.Item item = pack.items.remove(position);
        PackStore.file(this, pack, item.file).delete();
        try {
            PackStore.updateTray(this, pack);
            PackStore.save(this, pack);
        } catch (IOException e) {
            Toast.makeText(this, "Falha ao salvar: " + describe(e), Toast.LENGTH_LONG).show();
        }
        status.setText("");
        refresh();
    }

    private void confirmDeletePack() {
        if (busy) {
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage("Excluir o pacote \"" + pack.name + "\"? As figurinhas já adicionadas ao WhatsApp continuam lá.")
                .setPositiveButton("Excluir", (dialog, which) -> {
                    PackStore.delete(this, pack);
                    finish();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private static final class SquareImageView extends ImageView {
        SquareImageView(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, widthMeasureSpec);
        }
    }

    private final class StickerAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return pack.items.size();
        }

        @Override
        public Object getItem(int position) {
            return pack.items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final SquareImageView view = convertView instanceof SquareImageView
                    ? (SquareImageView) convertView
                    : new SquareImageView(PackActivity.this);
            view.setScaleType(ImageView.ScaleType.FIT_CENTER);
            view.setBackgroundColor(0x22FFFFFF);

            final String file = pack.items.get(position).file;
            view.setTag(file);
            Bitmap cached = thumbs.get(file);
            if (cached != null) {
                view.setImageBitmap(cached);
                return view;
            }
            view.setImageDrawable(null);
            final File path = PackStore.file(PackActivity.this, pack, file);
            thumbPool.execute(() -> {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 2;
                final Bitmap bitmap = BitmapFactory.decodeFile(path.getAbsolutePath(), options);
                if (bitmap == null) {
                    return;
                }
                thumbs.put(file, bitmap);
                runOnUiThread(() -> {
                    if (file.equals(view.getTag())) {
                        view.setImageBitmap(bitmap);
                    }
                });
            });
            return view;
        }
    }
}
