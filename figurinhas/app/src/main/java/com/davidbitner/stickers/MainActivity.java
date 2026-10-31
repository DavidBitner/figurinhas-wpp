package com.davidbitner.stickers;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private final List<Pack> packs = new ArrayList<>();
    private PackAdapter adapter;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int pad = Ui.dp(this, 16);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = Ui.text(this, "Figurinhas", 28, true);
        TextView subtitle = Ui.text(this, "Crie pacotes sem borda e adicione ao WhatsApp", 14, false);
        subtitle.setAlpha(0.7f);
        subtitle.setPadding(0, 0, 0, Ui.dp(this, 8));

        empty = Ui.text(this, "Nenhum pacote ainda. Toque em \"Novo pacote\" para começar.", 15, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, Ui.dp(this, 48), 0, 0);

        adapter = new PackAdapter();
        ListView list = new ListView(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> openPack(packs.get(position)));

        Button create = new Button(this);
        create.setText("Novo pacote");
        create.setOnClickListener(v -> askNewPack());

        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(subtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(empty, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(create, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        packs.clear();
        packs.addAll(PackStore.loadAll(this));
        empty.setVisibility(packs.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    private void openPack(Pack pack) {
        Intent intent = new Intent(this, PackActivity.class);
        intent.putExtra(PackActivity.EXTRA_ID, pack.id);
        startActivity(intent);
    }

    private void askNewPack() {
        int pad = Ui.dp(this, 20);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);

        final EditText name = new EditText(this);
        name.setHint("Nome do pacote");
        name.setSingleLine(true);

        final CheckBox animated = new CheckBox(this);
        animated.setText("Pacote animado (GIF ou vídeo)");

        box.addView(name);
        box.addView(animated);

        new AlertDialog.Builder(this)
                .setTitle("Novo pacote")
                .setView(box)
                .setPositiveButton("Criar", (dialog, which) -> {
                    String value = name.getText().toString().trim();
                    if (value.isEmpty()) {
                        value = "Meu pacote";
                    }
                    if (value.length() > 128) {
                        value = value.substring(0, 128);
                    }
                    try {
                        openPack(PackStore.create(this, value, animated.isChecked()));
                    } catch (IOException e) {
                        Toast.makeText(this, "Não consegui criar o pacote: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private final class PackAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return packs.size();
        }

        @Override
        public Object getItem(int position) {
            return packs.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Pack pack = packs.get(position);
            int pad = Ui.dp(MainActivity.this, 12);
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, pad, 0, pad);

            TextView name = Ui.text(MainActivity.this, pack.name, 18, true);
            String kind = pack.animated ? "animadas" : "estáticas";
            TextView detail = Ui.text(MainActivity.this,
                    pack.items.size() + " figurinhas " + kind, 13, false);
            detail.setAlpha(0.7f);

            row.addView(name);
            row.addView(detail);
            return row;
        }
    }
}
