package nl.mikekemmink.noodverlichting.nen3140;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.ui.BaseToolbarActivity;

public class StroomOverzichtActivity extends BaseToolbarActivity {

    private RecyclerView rv;
    private View empty;
    private StroomOverzichtAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);



        // ✳️ Kies palet + Up-knop (groen NOOD of blauw NEN)
        applyPalette(Palette.NEN);      // of Palette.NEN als je dit scherm blauw wilt
        setUpEnabled(true);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.title_stroom_overzicht);
        }

        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StroomOverzichtAdapter();
        rv.setAdapter(adapter);

        adapter.setOnItemClick(e -> {
            try {
                Intent i = new Intent(this, MeasurementsActivity.class);
                i.putExtra("mode", "edit");
                i.putExtra("entry_json", e.toJson().toString());
                startActivity(i);
            } catch (org.json.JSONException ex) {
                Toast.makeText(this, "Kan meting niet openen: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override public boolean onMove(RecyclerView rv, RecyclerView.ViewHolder vh, RecyclerView.ViewHolder t) { return false; }
            @Override public void onSwiped(RecyclerView.ViewHolder vh, int dir) {
                StroomWaardeEntry e = adapter.getItem(vh.getAdapterPosition());
                StroomRepo.deleteByKast(StroomOverzichtActivity.this, e.kastNaam);
                load();
                Toast.makeText(StroomOverzichtActivity.this, R.string.msg_deleted, Toast.LENGTH_SHORT).show();
            }
        }).attachToRecyclerView(rv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
        // optioneel: applyPalette(Palette.NOOD); // om altijd consistent te houden bij terugkeren
    }

    private void load() {
        File f = new File(getFilesDir(), "stroomwaardes.json");
        Log.d("StroomOverzicht", "FILE path=" + f.getAbsolutePath() + " exists=" + f.exists() + " size=" + f.length());

        Toast.makeText(this,
                getString(R.string.msg_path_exists, f.getAbsolutePath(), String.valueOf(f.exists()), String.valueOf(f.length())),
                Toast.LENGTH_LONG).show();

        List<StroomWaardeEntry> list = StroomRepo.getAll(this);
        Log.d("StroomOverzicht", "ADAPTER submit size=" + list.size());
        adapter.submit(list);

        empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ===== Gebruik de compat-hooks voor het Activity-menu =====
    @Override
    protected int getActivityToolbarMenuRes() {
        return R.menu.menu_stroom_overzicht;
    }

    @Override
    protected boolean onActivityToolbarItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_export_csv) {
            exportCsv();
            return true;
        }
        return false;
    }
    // ==========================================================

    private void exportCsv() {
        List<StroomWaardeEntry> list = StroomRepo.getAll(this);
        if (list.isEmpty()) {
            Toast.makeText(this, R.string.msg_no_data_to_export, Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        // Header
        sb.append("kast,id,l1,l2,l3,n,pe,datum\n");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        for (StroomWaardeEntry e : list) {
            sb.append(csv(e.kastNaam)).append(',')
                    .append(csv(e.id)).append(',')
                    .append(e.l1).append(',')
                    .append(e.l2).append(',')
                    .append(e.l3).append(',')
                    .append(e.n).append(',')
                    .append(e.pe).append(',')
                    .append(csv(sdf.format(new Date(e.timestamp)))).append('\n');
        }

        File dir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS);
        if (dir != null && !dir.exists()) dir.mkdirs();
        File out = new File(dir, "stroomwaardes_" + System.currentTimeMillis() + ".csv");

        try (FileWriter fw = new FileWriter(out)) {
            fw.write(sb.toString());
            Toast.makeText(this, getString(R.string.msg_export_ok, out.getAbsolutePath()), Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
            Toast.makeText(this, getString(R.string.msg_export_fail, ex.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private static String csv(String s) {
        if (s == null) return "";
        boolean wrap = s.contains(",") || s.contains("\"") || s.contains("\n");
        String x = s.replace("\"", "\"\"");
        return wrap ? "\"" + x + "\"" : x;
    }
}