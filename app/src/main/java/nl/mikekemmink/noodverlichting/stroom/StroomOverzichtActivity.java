package nl.mikekemmink.noodverlichting.stroom;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import nl.mikekemmink.noodverlichting.R;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import nl.mikekemmink.noodverlichting.stroom.StroomRepo;
import nl.mikekemmink.noodverlichting.stroom.StroomWaardeEntry;
import nl.mikekemmink.noodverlichting.ui.MeasurementsActivity;

public class StroomOverzichtActivity extends AppCompatActivity {

    private RecyclerView rv;
    private View empty;
    private StroomOverzichtAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stroom_overzicht);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Stroomwaardes");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        rv = findViewById(R.id.rvStroom);
        empty = findViewById(R.id.emptyView);

        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StroomOverzichtAdapter();
        rv.setAdapter(adapter);

        adapter.setOnItemClick(e -> {
            try {
                Intent i = new Intent(this, MeasurementsActivity.class); // <-- jouw invoer-Activity hier
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
                Toast.makeText(StroomOverzichtActivity.this, "Verwijderd", Toast.LENGTH_SHORT).show();
            }
        }).attachToRecyclerView(rv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        File f = new File(getFilesDir(), "stroomwaardes.json");
        Log.d("StroomOverzicht", "FILE path=" + f.getAbsolutePath() + " exists=" + f.exists() + " size=" + f.length());

        Toast.makeText(this,
                "Pad: " + f.getAbsolutePath() + "\nBestaat: " + f.exists() + "  size=" + f.length(),
                Toast.LENGTH_LONG).show();

        List<StroomWaardeEntry> list = StroomRepo.getAll(this);

        Log.d("StroomOverzicht", "ADAPTER submit size=" + list.size());
        adapter.submit(list);

        empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_stroom_overzicht, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) { onBackPressed(); return true; }
        if (id == R.id.action_export_csv) { exportCsv(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void exportCsv() {
        List<StroomWaardeEntry> list = StroomRepo.getAll(this);
        if (list.isEmpty()) {
            Toast.makeText(this, "Geen data om te exporteren", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("kast,id,l1,l2,l3,n,pe,datum ");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        for (StroomWaardeEntry e : list) {
            sb.append(csv(e.kastNaam)).append(',')
              .append(csv(e.id)).append(',')
              .append(e.l1).append(',')
              .append(e.l2).append(',')
              .append(e.l3).append(',')
              .append(e.n).append(',')
              .append(e.pe).append(',')
              .append(csv(sdf.format(new Date(e.timestamp)))).append(' ');
        }

        File dir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS);
        if (dir != null && !dir.exists()) dir.mkdirs();
        File out = new File(dir, "stroomwaardes_" + System.currentTimeMillis() + ".csv");

        try {
            FileWriter fw = new FileWriter(out);
            fw.write(sb.toString());
            fw.close();
            Toast.makeText(this, "Geëxporteerd naar: " + out.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
            Toast.makeText(this, "Export mislukt: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }


    private static String csv(String s) {
        if (s == null) return "";
        boolean wrap = s.contains(",") || s.contains("\"") || s.contains("\n");
        String x = s.replace("\"", "\"\"");
        return wrap ? "\"" + x + "\"" : x;
    }

}
