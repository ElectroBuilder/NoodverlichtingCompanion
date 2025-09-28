
package nl.mikekemmink.noodverlichting.ui;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.data.DBField;

public class DefectListActivity extends AppCompatActivity {

    public static final String EXTRA_INSPECTIE_ID = "inspectie_id";
    public static final String EXTRA_TITEL = "titel";

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_defect_list);

        int inspectieId = getIntent().getIntExtra(EXTRA_INSPECTIE_ID, -1);
        String titel = getIntent().getStringExtra(EXTRA_TITEL);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(titel != null ? titel : ("Gebreken #" + inspectieId));
        }

        RecyclerView rv = findViewById(R.id.rvDefects);
        rv.setLayoutManager(new LinearLayoutManager(this));
        DefectAdapter adapter = new DefectAdapter();
        rv.setAdapter(adapter);

        try {
            SQLiteDatabase db = DBField.getInstance(this).getReadableDatabase();
            Cursor c = db.rawQuery("SELECT rowid as _id, omschrijving, datum, foto_pad FROM gebreken WHERE inspectie_id = ? ORDER BY datum DESC, rowid DESC", new String[]{ String.valueOf(inspectieId) });
            List<DefectAdapter.Item> list = new ArrayList<>();
            int idxId = c.getColumnIndex("_id"); int idxOms = c.getColumnIndex("omschrijving"); int idxDt = c.getColumnIndex("datum"); int idxFoto = c.getColumnIndex("foto_pad");
            while (c.moveToNext()) { DefectAdapter.Item it = new DefectAdapter.Item(); it.id = idxId>=0?c.getLong(idxId):0L; it.omschrijving = idxOms>=0?c.getString(idxOms):null; it.datum = idxDt>=0?c.getString(idxDt):null; it.fotoPad = idxFoto>=0?c.getString(idxFoto):null; list.add(it);} c.close();
            adapter.submit(list); if (list.isEmpty()) Toast.makeText(this, "Geen gebreken gevonden", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { Toast.makeText(this, "Lezen mislukt: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    @Override public boolean onSupportNavigateUp() { onBackPressed(); return true; }
}
