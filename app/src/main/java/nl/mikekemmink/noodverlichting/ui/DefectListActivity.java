
package nl.mikekemmink.noodverlichting.ui;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;

import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.data.DBField;

import android.util.Log;
public class DefectListActivity extends AppCompatActivity {

    private ActivityResultLauncher<Intent> editLauncher;

    public static final String EXTRA_INSPECTIE_ID = "inspectie_id";
    public static final String EXTRA_TITEL = "titel";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_defect_list);

            // Extras
            int inspectieId = getIntent().getIntExtra(EXTRA_INSPECTIE_ID, -1);
            String titel = getIntent().getStringExtra(EXTRA_TITEL);

            // Toolbar
            MaterialToolbar tb = findViewById(R.id.toolbar);
            setSupportActionBar(tb);
            ActionBar ab = getSupportActionBar();
            if (ab != null) ab.setDisplayHomeAsUpEnabled(true);
            String titleStr = (titel != null && !titel.isEmpty()) ? titel : "Gebreken #" + inspectieId;
            tb.setTitle(titleStr);
            tb.setNavigationOnClickListener(v -> finish());

            // ActivityResult launcher (voor bewerken)
            editLauncher = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> { if (result.getResultCode() == RESULT_OK) reload(); }
            );

        // 3) InspectieId valideren (vroeg terugmelden i.p.v. leeg scherm)
        if (inspectieId <= 0) {
            Toast.makeText(this, "Geen inspectie_id ontvangen → kan geen gebreken tonen", Toast.LENGTH_LONG).show();
            finish(); // of laat leeg scherm staan, maar expliciet is beter
            return;
        }

        RecyclerView rv = findViewById(R.id.rvDefects);
        rv.setLayoutManager(new LinearLayoutManager(this));
        DefectAdapter adapter = new DefectAdapter();
        rv.setAdapter(adapter);


        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                                  RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                int pos = viewHolder.getAdapterPosition();
                DefectAdapter.Item item = adapter.getItemAt(pos);

                if (direction == ItemTouchHelper.LEFT) {
                    deleteDefect(item.id);
                } else if (direction == ItemTouchHelper.RIGHT) {
                    editDefect(item.id);
                }
            }

            @Override
            public void onChildDraw(Canvas c, RecyclerView recyclerView,
                                    RecyclerView.ViewHolder viewHolder, float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {

                View itemView = viewHolder.itemView;
                Paint paint = new Paint();

                int iconMargin = (itemView.getHeight() - 64) / 2; // 64px icoonhoogte
                int iconSize = 64;

                if (dX > 0) { // swipe naar rechts (bewerken)
                    paint.setColor(Color.parseColor("#4CAF50")); // groen
                    c.drawRect(itemView.getLeft(), itemView.getTop(), itemView.getLeft() + dX,
                            itemView.getBottom(), paint);

                    Drawable icon = ContextCompat.getDrawable(recyclerView.getContext(), R.drawable.ic_edit_24);
                    if (icon != null) {
                        int top = itemView.getTop() + iconMargin;
                        int left = itemView.getLeft() + iconMargin;
                        icon.setBounds(left, top, left + iconSize, top + iconSize);
                        icon.draw(c);
                    }

                } else if (dX < 0) { // swipe naar links (verwijderen)
                    paint.setColor(Color.parseColor("#F44336")); // rood
                    c.drawRect(itemView.getRight() + dX, itemView.getTop(), itemView.getRight(),
                            itemView.getBottom(), paint);

                    Drawable icon = ContextCompat.getDrawable(recyclerView.getContext(), R.drawable.ic_delete_24);
                    if (icon != null) {
                        int top = itemView.getTop() + iconMargin;
                        int right = itemView.getRight() - iconMargin;
                        icon.setBounds(right - iconSize, top, right, top + iconSize);
                        icon.draw(c);
                    }
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        };


        new ItemTouchHelper(swipeCallback).attachToRecyclerView(rv);

        try {
            SQLiteDatabase db = DBField.getInstance(this).getReadableDatabase();
            String[] args = new String[]{ String.valueOf(inspectieId) };
            Cursor c = db.rawQuery(
                    "SELECT id as _id, omschrijving, datum, foto_pad " +
                            "FROM gebreken WHERE inspectie_id = ? ORDER BY datum DESC, rowid DESC", args);

            List<DefectAdapter.Item> list = new ArrayList<>();
            int idxId = c.getColumnIndex("_id");
            int idxOms = c.getColumnIndex("omschrijving");
            int idxDt = c.getColumnIndex("datum");
            int idxFoto = c.getColumnIndex("foto_pad");

            while (c.moveToNext()) {
                DefectAdapter.Item it = new DefectAdapter.Item();
                it.id = idxId >= 0 ? c.getLong(idxId) : 0L;
                it.omschrijving = idxOms >= 0 ? c.getString(idxOms) : null;
                it.datum = idxDt >= 0 ? c.getString(idxDt) : null;
                it.fotoPad = idxFoto >= 0 ? c.getString(idxFoto) : null;
                list.add(it);
            }
            c.close();

            Log.d("DefectList", "Query-resultaat voor inspectieId=" + inspectieId + " → " + list.size() + " records");
            adapter.submit(list);

            if (list.isEmpty()) {
                Toast.makeText(this, "Geen gebreken voor ID " + inspectieId, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("DefectList", "Lezen mislukt", e);
            Toast.makeText(this, "Lezen mislukt: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

@Override public boolean onSupportNavigateUp() { onBackPressed(); return true; }
    private void reload() {
        int inspectieId = getIntent().getIntExtra(EXTRA_INSPECTIE_ID, -1);
        RecyclerView rv = findViewById(R.id.rvDefects);
        DefectAdapter adapter = (DefectAdapter) rv.getAdapter();

        if (adapter == null) return;

        try {
            SQLiteDatabase db = DBField.getInstance(this).getReadableDatabase();
            Cursor c = db.rawQuery("SELECT id as _id, omschrijving, datum, foto_pad FROM gebreken WHERE inspectie_id = ? ORDER BY datum DESC, rowid DESC", new String[]{ String.valueOf(inspectieId) });
            List<DefectAdapter.Item> list = new ArrayList<>();
            int idxId = c.getColumnIndex("_id");
            int idxOms = c.getColumnIndex("omschrijving");
            int idxDt = c.getColumnIndex("datum");
            int idxFoto = c.getColumnIndex("foto_pad");

            while (c.moveToNext()) {
                DefectAdapter.Item it = new DefectAdapter.Item();
                it.id = idxId >= 0 ? c.getLong(idxId) : 0L;
                it.omschrijving = idxOms >= 0 ? c.getString(idxOms) : null;
                it.datum = idxDt >= 0 ? c.getString(idxDt) : null;
                it.fotoPad = idxFoto >= 0 ? c.getString(idxFoto) : null;
                list.add(it);
            }
            c.close();
            adapter.submit(list);
            if (list.isEmpty()) Toast.makeText(this, "Geen gebreken gevonden", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Herladen mislukt: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void deleteDefect(long id) {
        SQLiteDatabase db = DBField.getInstance(this).getWritableDatabase();
        db.delete("gebreken", "id = ?", new String[]{ String.valueOf(id) });
        reload(); // herlaad de lijst
    }

    private void editDefect(long id) {
        Intent i = new Intent(this, AddDefectActivity.class);
        i.putExtra(AddDefectActivity.EXTRA_DEFECT_ID, id);

        if (editLauncher != null) {
            editLauncher.launch(i);
        } else {
            // Fallback: zou niet moeten gebeuren, maar voorkomt crash en helpt debuggen
            Log.w("DefectList", "editLauncher was null; fallback naar startActivity()");
            startActivity(i);
        }
    }

}
