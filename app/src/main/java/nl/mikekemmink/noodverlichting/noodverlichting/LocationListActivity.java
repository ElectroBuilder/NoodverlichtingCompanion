package nl.mikekemmink.noodverlichting.noodverlichting;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.noodverlichting.data.DBInspecties;
import nl.mikekemmink.noodverlichting.ui.BaseToolbarActivity;
import nl.mikekemmink.noodverlichting.ui.LocationsAdapter;

public class LocationListActivity extends BaseToolbarActivity implements LocationsAdapter.OnItemClick {

    public static final String EXTRA_LOCATIE = "nl.mikekemmink.noodverlichting.extra.LOCATIE";

    private final List<String> all = new ArrayList<>();
    private final List<String> filtered = new ArrayList<>();

    private LocationsAdapter adapter;
    private RecyclerView recycler;
    private View emptyState;

    private boolean showDefects = false;   // bestaande toggle uit IToolbarActions
    private boolean isGrid = false;        // nieuwe toggle
    private SharedPreferences prefs;

    private GridSpacingItemDecoration gridDecoration;
    private static final int GRID_SPACING_DP = 12;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Plaats de content-layout onder de single toolbar (MOET de juiste IDs bevatten)
        setContentLayout(R.layout.activity_location_list);

        // Toolbar / status
        setUpEnabled(true);
        applyPalette(Palette.NOOD);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.title_locations);
        }

        // Views
        recycler   = findViewById(R.id.recycler);
        emptyState = findViewById(R.id.emptyState);

        // Prefs (list/grid onthouden)
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        isGrid = prefs.getBoolean("view_grid_locations", false);

        // Adapter
        adapter = new LocationsAdapter(filtered, isGrid, this);
        recycler.setAdapter(adapter);
        updateLayoutManager();

        // Swipe to refresh
        androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipe = findViewById(R.id.swipeRefresh);
        swipe.setOnRefreshListener(() -> {
            reloadData();
            swipe.setRefreshing(false);
        });

        // Filterchips
        setupChips();

        // Data laden
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
        // Zorg dat het menu (titel/icon van toggle) wordt bijgewerkt via onPrepareActivityToolbarMenu
        invalidateOptionsMenu();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    // Toolbar-menu handler
    private boolean onToolbarMenuItem(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_toggle_view) {
            isGrid = !isGrid;
            prefs.edit().putBoolean("view_grid_locations", isGrid).apply();
            updateLayoutManager();
            adapter.setGrid(isGrid);
            adapter.notifyDataSetChanged();
            // Als je ic_list_24 / ic_grid_24 drawables hebt, kun je ze hier zetten:
            // item.setIcon(isGrid ? R.drawable.ic_list_24 : R.drawable.ic_grid_24);
            item.setTitle(isGrid ? "Lijst weergeven" : "Grid weergeven");
            return true;
        }
        return false;
    }

    private void updateLayoutManager() {
        // Oude decoration weghalen (voorkomt dubbele spacing)
        if (gridDecoration != null) {
            recycler.removeItemDecoration(gridDecoration);
            gridDecoration = null;
        }
        if (isGrid) {
            int span = getSpanCount();
            recycler.setLayoutManager(new GridLayoutManager(this, span));
            gridDecoration = new GridSpacingItemDecoration(span, dpToPx(GRID_SPACING_DP), true);
            recycler.addItemDecoration(gridDecoration);
        } else {
            recycler.setLayoutManager(new LinearLayoutManager(this));
        }
    }

    private int getSpanCount() {
        // Mik op ~180dp tegelbreedte
        int minWidthPx = dpToPx(180);
        int width = getResources().getDisplayMetrics().widthPixels;
        return Math.max(2, width / minWidthPx);
    }

    private int dpToPx(int dp) {
        float d = getResources().getDisplayMetrics().density;
        return Math.round(dp * d);
    }

    private void setupChips() {
        Chip chipAll = findViewById(R.id.chipAll);
        Chip chipWithIssues = findViewById(R.id.chipWithIssues);
        Chip chipNoIssues = findViewById(R.id.chipNoIssues);

        View.OnClickListener listener = v -> applyFilter();
        if (chipAll != null)        chipAll.setOnClickListener(listener);
        if (chipWithIssues != null) chipWithIssues.setOnClickListener(listener);
        if (chipNoIssues != null)   chipNoIssues.setOnClickListener(listener);
    }

    private void applyFilter() {
        Chip chipAll = findViewById(R.id.chipAll);
        Chip chipWithIssues = findViewById(R.id.chipWithIssues);

        filtered.clear();
        if (chipAll != null && chipAll.isChecked()) {
            filtered.addAll(all);
        } else if (chipWithIssues != null && chipWithIssues.isChecked()) {
            // TODO: echte filter op locaties met gebreken
            filtered.addAll(all);
        } else {
            // TODO: echte filter op locaties zonder gebreken
            filtered.addAll(all);
        }
        adapter.notifyDataSetChanged();
        emptyState.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void reloadData() {
        SQLiteDatabase db = DBInspecties.tryOpenReadOnly(this);
        if (db != null) {
            all.clear();
            all.addAll(DBInspecties.getDistinctLocaties(db));
            db.close();
        }
        applyFilter();
    }

    private void filter(String q) {
        filtered.clear();
        String qq = q.toLowerCase();
        for (String s : all) {
            if (qq.isEmpty() || s.toLowerCase().contains(qq)) filtered.add(s);
        }
        adapter.notifyDataSetChanged();
        emptyState.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // Klik op een locatie -> naar armaturenlijst
    @Override
    public void onClick(String value) {
        Intent i = new Intent(this, FixtureListActivity.class);
        i.putExtra(LocationListActivity.EXTRA_LOCATIE, value);
        startActivity(i);
    }

    // IToolbarActions (bestaand mechanisme)
    @Override public boolean isDefectsShown() { return showDefects; }
    @Override public void onToggleDefects(boolean show) { showDefects = show; }
    @Override public void onColumnsClicked() { /* niet van toepassing voor locaties */ }

    // ===== Activity-menu via BaseToolbarActivity compat-hooks =====
    @Override
    protected int getActivityToolbarMenuRes() {
        return R.menu.menu_location_list; // nieuw menubestand
    }

    @Override
    protected void onPrepareActivityToolbarMenu(@NonNull Menu menu) {
        MenuItem toggle = menu.findItem(R.id.action_toggle_view);
        if (toggle != null) {
            // Als je drawables ic_list_24/ic_grid_24 hebt, kun je ze hier zetten:
            // toggle.setIcon(isGrid ? R.drawable.ic_list_24 : R.drawable.ic_grid_24);
            toggle.setTitle(isGrid ? "Lijst weergeven" : "Grid weergeven");
        }
    }

    @Override
    protected boolean onActivityToolbarItemSelected(@NonNull MenuItem item) {
        return onToolbarMenuItem(item);
    }
}