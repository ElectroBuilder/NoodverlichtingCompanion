package nl.mikekemmink.noodverlichting.ui;

import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;

import nl.mikekemmink.noodverlichting.BaseActivity;
import nl.mikekemmink.noodverlichting.IToolbarActions;
import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.data.DBInspecties;

public class LocationListActivity extends BaseActivity implements SimpleAdapter.OnClick, IToolbarActions {

    public static final String EXTRA_LOCATIE = "nl.mikekemmink.noodverlichting.extra.LOCATIE";

    private final List<String> all = new ArrayList<>();
    private final List<String> filtered = new ArrayList<>();
    private SimpleAdapter adapter;

    private boolean showDefects = false; // voor de toolbar-toggle

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1) Outer layout met AppBar + Toolbar
        setContentView(R.layout.activity_with_toolbar);

        // 2) Toolbar koppelen (één keer per Activity)

        MaterialToolbar tb = findViewById(R.id.toolbar);
        attachToolbar(tb);

        // Terugknop laten werken
        tb.setNavigationOnClickListener(v -> finish());


        // 3) JOUW content in de container zetten
        getLayoutInflater().inflate(
                R.layout.activity_location_list,
                findViewById(R.id.content_container),
                true
        );

        // ---- Originele init-code (ongewijzigd) ----
        RecyclerView recycler = findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SimpleAdapter(filtered, this);
        recycler.setAdapter(adapter);

        SQLiteDatabase db = DBInspecties.tryOpenReadOnly(this);
        if (db != null) {
            all.clear();
            all.addAll(DBInspecties.getDistinctLocaties(db));
            db.close();
        }
        filter("");
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Toolbar-knoppen zichtbaar maken + state laten syncen
        setToolbarActions(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        setToolbarActions(null);
    }

    private void filter(String q) {
        filtered.clear();
        String qq = q.toLowerCase();
        for (String s : all) {
            if (qq.isEmpty() || s.toLowerCase().contains(qq)) filtered.add(s);
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onItemClick(String value) {
        Intent i = new Intent(this, FixtureListActivity.class);
        i.putExtra(LocationListActivity.EXTRA_LOCATIE, value);
        startActivity(i);
    }

    // -------- IToolbarActions ----------
    @Override public boolean isDefectsShown() { return showDefects; }

    @Override public void onToggleDefects(boolean show) {
        showDefects = show;
        // TODO: als je hier iets wilt doen (bijv. locaties met open gebreken filteren/highlighten)
        // Voor nu geen-opdracht: de toggle onthoudt alleen de stand.
    }

    @Override public void onColumnsClicked() {
        // Locatiescherm heeft (waarschijnlijk) geen kolominstellingen.
        // Je kunt hier desgewenst een dialoog tonen of niets doen.
        // Bijvoorbeeld:
        // Toast.makeText(this, "Kolommen zijn alleen beschikbaar bij Armaturen", Toast.LENGTH_SHORT).show();
    }
}