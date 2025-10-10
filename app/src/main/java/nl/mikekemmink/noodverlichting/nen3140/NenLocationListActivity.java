package nl.mikekemmink.noodverlichting.nen3140;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.nen3140.export.ExportOptions;
import nl.mikekemmink.noodverlichting.nen3140.export.NenExporter;
import nl.mikekemmink.noodverlichting.ui.BaseToolbarActivity;
import nl.mikekemmink.noodverlichting.ui.LocationsAdapterIds;
import nl.mikekemmink.noodverlichting.ui.ProgressDialogFragment;
import java.util.concurrent.atomic.AtomicBoolean;

public class NenLocationListActivity extends BaseToolbarActivity
        implements ProgressDialogFragment.OnCancelRequested {

    private NenStorage storage;
    private LocationsAdapterIds adapter;

    // Data (bron: NenStorage)
    private final List<NenLocation> locations = new ArrayList<>();

    //export
    private static final String TAG_PROGRESS = "export_progress";
    private ExecutorService exec;
    private Handler main;
    private AlertDialog progressDialog;

    private final AtomicBoolean cancelFlag = new AtomicBoolean(false);
    // UI
    private RecyclerView rv;
    private View emptyState;
    private SwipeRefreshLayout swipe;
    private ChipGroup chips;



    // View state
    private boolean isGrid = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.nen_locaties);
        setTitle("Locaties (NEN3140)");
        applyPalette(Palette.NEN);
        setUpEnabled(true);

        if (savedInstanceState != null) {
            isGrid = savedInstanceState.getBoolean("isGrid", false);
        }

        storage = new NenStorage(this);

        rv         = findViewById(R.id.recyclerView);
        emptyState = findViewById(R.id.emptyState);
        swipe      = findViewById(R.id.swipeRefresh);
        chips      = findViewById(R.id.chipsFilter);

        // Data laden
        locations.clear();
        locations.addAll(storage.loadLocations());

        // Adapter: unieke IDs + NEN layouts
        adapter = new LocationsAdapterIds(
                toItems(locations),                        // (id, title)
                isGrid,
                (id, title) -> {                           // klik -> naar bordenlijst
                    Intent i = new Intent(this, NenBoardsActivity.class);
                    i.putExtra("locationId", id);
                    startActivity(i);
                },
                R.layout.nen_locaties_lijst,            // lijst-item  (met @id/txtTitle)
                R.layout.nen_locaties_grid            // grid-item   (met @id/txtTitle)
        );

        // Long-click menu (Bewerken / Verwijderen)
        adapter.setOnItemLongClick((id, title) -> {
            NenLocation loc = findById(id);
            if (loc == null) return;
            String[] items = new String[]{"Bewerken", "Verwijderen", "Exporteer"};
            new AlertDialog.Builder(this)
                    .setTitle(loc.getName())
                    .setItems(items, (d, which) -> {
                        if (which == 0) {
                            showRenameLocationDialog(loc);
                        } else if (which == 1) {
                            confirmDeleteLocation(loc);
                        } else if (which == 2) {
                            exportOneLocation(loc.getId(), loc.getName());
                        }
                    })
                    .show();
        });
        exec = Executors.newSingleThreadExecutor();
        main = new Handler(Looper.getMainLooper());
        rv.setAdapter(adapter);
        applyLayoutManager();      // lijst of grid
        toggleEmptyState();        // empty‑state check

        // Swipe to refresh
        swipe.setOnRefreshListener(() -> {
            reloadData();
            swipe.setRefreshing(false);
            toggleEmptyState();
        });

        // Chips (hook; pas aan met jouw echte filter)
        chips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            // TODO: filter toepassen (Alle / Met gebreken / Zonder gebreken)
            reloadData();
            toggleEmptyState();
        });

        // FAB = Locatie toevoegen
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> showAddLocationDialog());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("isGrid", isGrid);
    }

    // Toolbar menu (lijst/grid toggle)
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_nen_locations, menu);
        updateToggleIcon(menu.findItem(R.id.action_toggle_layout));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_toggle_layout) {
            isGrid = !isGrid;
            applyLayoutManager();
            updateToggleIcon(item);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateToggleIcon(MenuItem item) {
        if (item == null) return;
        // Gebruik de iconen die je in /res/drawable hebt staan
        item.setIcon(isGrid ? R.drawable.ic_list_24 : R.drawable.ic_grid_24);
        item.setTitle(isGrid ? "Lijst" : "Grid");
    }

    private void applyLayoutManager() {
        if (isGrid) {
            rv.setLayoutManager(new GridLayoutManager(this, getSpanCount()));
            adapter.setGrid(true);
        } else {
            rv.setLayoutManager(new LinearLayoutManager(this));
            adapter.setGrid(false);
        }
        rv.setHasFixedSize(true);
    }

    private int getSpanCount() {
        int swDp = getResources().getConfiguration().screenWidthDp;
        return swDp >= 600 ? 3 : 2; // tablet vs. telefoon
    }

    private void reloadData() {
        locations.clear();
        locations.addAll(storage.loadLocations());
        adapter.setItems(toItems(locations));
    }

    private void toggleEmptyState() {
        emptyState.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    @Nullable
    private NenLocation findById(String id) {
        if (id == null) return null;
        for (NenLocation n : locations) {
            if (id.equals(n.getId())) return n;
        }
        return null;
    }

    /** Maak adapter-items op basis van model (garandeer niet‑null id/title). */
    private static List<LocationsAdapterIds.Item> toItems(List<NenLocation> locs) {
        List<LocationsAdapterIds.Item> out = new ArrayList<>();
        for (NenLocation n : locs) {
            String id    = n.getId() != null ? n.getId() : UUID.randomUUID().toString();
            String title = n.getName() != null ? n.getName() : "";
            out.add(new LocationsAdapterIds.Item(id, title));
        }
        return out;
    }

    // === Dialogs ===

    private void showAddLocationDialog() {
        final EditText input = new EditText(this);
        new AlertDialog.Builder(this)
                .setTitle("Locatie toevoegen")
                .setView(input)
                .setPositiveButton("Opslaan", (d, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        storage.addLocation(name);
                        reloadData();
                        toggleEmptyState();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showRenameLocationDialog(NenLocation loc) {
        final EditText input = new EditText(this);
        input.setText(loc.getName());
        new AlertDialog.Builder(this)
                .setTitle("Locatie bewerken")
                .setView(input)
                .setPositiveButton("Opslaan", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        storage.updateLocationName(loc.getId(), name);
                        reloadData();
                        toggleEmptyState();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDeleteLocation(NenLocation loc) {
        new AlertDialog.Builder(this)
                .setTitle("Verwijderen?")
                .setMessage("Locatie \"" + loc.getName() + "\" en alle bijbehorende borden, metingen, gebreken en foto’s worden verwijderd. Weet je het zeker?")
                .setPositiveButton("Verwijderen", (d, w) -> {
                    storage.deleteLocation(loc.getId());
                    reloadData();
                    toggleEmptyState();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
    private void exportOneLocation(String locationId, String locationName) {
        cancelFlag.set(false); // reset bij start
        showProgressDialog("Exporteren…", "Scannen…");

        exec.execute(() -> {
            try {
                ExportOptions opts = new ExportOptions();
                opts.locationIds.add(locationId);
                opts.maxLongEdgePx = 1600;
                opts.jpegQuality   = 80;

                // (Nieuw) basisnaam voor de ZIP:
                // - als je de UI-titel wil gebruiken:
                opts.outputBaseName = (locationName != null && !locationName.trim().isEmpty())
                        ? locationName.trim()
                        : locationId; // fallback

                File zip = NenExporter.exportToZip(
                        this, /* outDirOrNull */ null, opts,
                        new NenExporter.ProgressCallback() {
                            @Override public void onProgress(String phase, int cur, int total) {
                                // >>> HIER komt jouw visuele update <<<
                                main.post(() -> updateProgressUI(phase, cur, total));
                            }
                            @Override public boolean isCancelled() {
                                return cancelFlag.get();
                            }
                        });

                main.post(() -> {
                    hideProgressDialog();
                    Toast.makeText(this, "Export gereed: " + zip.getName(), Toast.LENGTH_LONG).show();
                    shareZip(zip);
                });
            } catch (InterruptedException ie) {
                main.post(() -> {
                    hideProgressDialog();
                    Toast.makeText(this, "Export geannuleerd", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                e.printStackTrace();
                main.post(() -> {
                    hideProgressDialog();
                    Toast.makeText(this, "Export mislukt: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void shareZip(File zip) {
        Uri uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",   // <-- in plaats van BuildConfig.APPLICATION_ID
                zip
        );

        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("application/zip");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, "NEN3140-export delen"));
    }

    private void showProgressDialog(String title, String message) {
        androidx.fragment.app.FragmentManager fm = getSupportFragmentManager();
        nl.mikekemmink.noodverlichting.ui.ProgressDialogFragment old =
                (nl.mikekemmink.noodverlichting.ui.ProgressDialogFragment) fm.findFragmentByTag("export_progress");
        if (old != null) old.dismissAllowingStateLoss();

        nl.mikekemmink.noodverlichting.ui.ProgressDialogFragment f =
                nl.mikekemmink.noodverlichting.ui.ProgressDialogFragment.newInstance(title, message);
        f.show(fm, "export_progress");
    }

    private void updateProgressUI(String phase, int cur, int total) {
        nl.mikekemmink.noodverlichting.ui.ProgressDialogFragment f =
                (nl.mikekemmink.noodverlichting.ui.ProgressDialogFragment) getSupportFragmentManager()
                        .findFragmentByTag("export_progress");
        if (f != null) f.updateProgress(phase, cur, total);
    }

    private void hideProgressDialog() {
        nl.mikekemmink.noodverlichting.ui.ProgressDialogFragment f =
                (nl.mikekemmink.noodverlichting.ui.ProgressDialogFragment) getSupportFragmentManager()
                        .findFragmentByTag("export_progress");
        if (f != null) f.dismissAllowingStateLoss();
    }
    @Override
    public void onCancelRequested() {
        cancelFlag.set(true);
    }
}