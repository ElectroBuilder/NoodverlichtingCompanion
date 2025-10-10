
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
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
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
import java.util.concurrent.atomic.AtomicBoolean;

import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.nen3140.export.ExportOptions;
import nl.mikekemmink.noodverlichting.nen3140.export.NenExporter;
import nl.mikekemmink.noodverlichting.nen3140.importer.NenImporter;
import nl.mikekemmink.noodverlichting.ui.BaseToolbarActivity;
import nl.mikekemmink.noodverlichting.ui.LocationsAdapterIds;
import nl.mikekemmink.noodverlichting.ui.ProgressDialogFragment;

public class NenLocationListActivity extends BaseToolbarActivity
        implements ProgressDialogFragment.OnCancelRequested {

    private NenStorage storage;
    private LocationsAdapterIds adapter;

    // Data
    private final List<NenLocation> locations = new ArrayList<>();

    // Export/Import infra
    private static final String TAG_PROGRESS = "export_progress";
    private ExecutorService exec;
    private Handler main;
    private final AtomicBoolean cancelFlag = new AtomicBoolean(false);

    // UI
    private RecyclerView rv;
    private View emptyState;
    private SwipeRefreshLayout swipe;
    private ChipGroup chips;

    // FAB menu
    private ActivityResultLauncher<String[]> pickZipLauncher;
    private static final int MENU_IMPORT = 1; // Locatie importeren
    private static final int MENU_ADD    = 2; // Locatie toevoegen

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
        rv = findViewById(R.id.recyclerView);
        emptyState = findViewById(R.id.emptyState);
        swipe = findViewById(R.id.swipeRefresh);
        chips = findViewById(R.id.chipsFilter);

        // Data laden
        locations.clear();
        locations.addAll(storage.loadLocations());

        // Adapter
        adapter = new LocationsAdapterIds(
                toItems(locations),
                isGrid,
                (id, title) -> {
                    Intent i = new Intent(this, NenBoardsActivity.class);
                    i.putExtra("locationId", id);
                    startActivity(i);
                },
                R.layout.nen_locaties_lijst,
                R.layout.nen_locaties_grid
        );

        // Long-click: Bewerken / Verwijderen / Exporteer
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
        applyLayoutManager();
        toggleEmptyState();

        // Swipe-to-refresh
        swipe.setOnRefreshListener(() -> {
            reloadData();
            swipe.setRefreshing(false);
            toggleEmptyState();
        });

        // Chips (placeholder)
        chips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            reloadData();
            toggleEmptyState();
        });

        // Register document picker voor ZIP
        pickZipLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        try {
                            getContentResolver().takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                            );
                        } catch (Exception ignore) {}
                        importZip(uri);
                    }
                }
        );

        // FAB => menu
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(this::showFabMenu);
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
        return swDp >= 600 ? 3 : 2;
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

    /** Maak adapter-items op basis van model */
    private static List<LocationsAdapterIds.Item> toItems(List<NenLocation> locs) {
        List<LocationsAdapterIds.Item> out = new ArrayList<>();
        for (NenLocation n : locs) {
            String id = n.getId() != null ? n.getId() : UUID.randomUUID().toString();
            String title = n.getName() != null ? n.getName() : "";
            out.add(new LocationsAdapterIds.Item(id, title));
        }
        return out;
    }

    // === FAB menu ===
    private void showFabMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, MENU_IMPORT, 0, "Locatie importeren");
        pm.getMenu().add(0, MENU_ADD,    1, "Locatie toevoegen");
        pm.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_IMPORT) {
                startImportZipPicker();
                return true;
            } else if (item.getItemId() == MENU_ADD) {
                showAddLocationDialog();
                return true;
            }
            return false;
        });
        pm.show();
    }

    private void startImportZipPicker() {
        // Toon alleen ZIP-bestanden (zoveel mogelijk)
        pickZipLauncher.launch(new String[]{
                "application/zip",
                "application/x-zip-compressed" // sommige providers/Windows-omgeving
        });
    }

    // === Import flow ===
    private void importZip(Uri uri) {
        cancelFlag.set(false);
        showProgressDialog("Importeren…", "Bestand controleren…");

        exec.execute(() -> {
            try {
                NenImporter.ImportResult res = NenImporter.importFromZip(
                        this, uri,
                        new NenImporter.ProgressCallback() {
                            @Override public void onProgress(String phase, int cur, int total) {
                                main.post(() -> updateProgressUI(phase, cur, total));
                            }
                            @Override public boolean isCancelled() { return cancelFlag.get(); }
                        }
                );

                main.post(() -> {
                    hideProgressDialog();
                    reloadData();
                    toggleEmptyState();
                    Toast.makeText(
                            this,
                            "Import gereed: " + res.locationsUpdated + " locaties, " +
                                    res.boardsFiles + " boards, " +
                                    res.measureFiles + " metingen, " +
                                    res.defectFiles + " gebreken, " +
                                    res.photosCopied + " foto’s",
                            Toast.LENGTH_LONG
                    ).show();
                });
            } catch (InterruptedException ie) {
                main.post(() -> {
                    hideProgressDialog();
                    Toast.makeText(this, "Import geannuleerd", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                e.printStackTrace();
                main.post(() -> {
                    hideProgressDialog();
                    Toast.makeText(this, "Import mislukt: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // === Export (ongewijzigd, maar met progress elders mogelijk) ===
    private void exportOneLocation(String locationId, String locationName) {
        cancelFlag.set(false);
        showProgressDialog("Exporteren…", "Scannen…");

        exec.execute(() -> {
            try {
                ExportOptions opts = new ExportOptions();
                opts.locationIds.add(locationId);
                File zip = NenExporter.exportToZip(
                        this, null, opts,
                        new NenExporter.ProgressCallback() {
                            @Override public void onProgress(String phase, int cur, int total) {
                                main.post(() -> updateProgressUI(phase, cur, total));
                            }
                            @Override public boolean isCancelled() { return cancelFlag.get(); }
                        }
                );

                main.post(() -> {
                    hideProgressDialog();
                    Toast.makeText(this, "Export gereed: " + zip.getName(), Toast.LENGTH_LONG).show();

                    // Automatisch publiceren naar Downloads
                    try {
                        // Je kunt hier een nette submap gebruiken
                        String subFolder = "NEN3140";
                        // Gebruik dezelfde zichtbare naam als het ZIP-bestand zelf:
                        String display = zip.getName();

                        // Schrijf naar Downloads
                        android.net.Uri uri = nl.mikekemmink.noodverlichting.nen3140.export.DownloadsPublisher
                                .saveZipToDownloads(this, zip, subFolder, display);

                        // (Optioneel) Openen of delen
                        // shareZip(zip);  // (de app-kopie)
                        // of: openViaUri(uri);

                        Toast.makeText(this, "Ook opgeslagen in Downloads/" + subFolder, Toast.LENGTH_SHORT).show();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        Toast.makeText(this, "Opslaan naar Downloads mislukt: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                    }

                    shareZip(zip);
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void shareZip(File zip) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", zip);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("application/zip");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, "NEN3140-export delen"));
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

    // ProgressDialogFragment cancel
    @Override
    public void onCancelRequested() {
        cancelFlag.set(true);
    }

    // Progress helpers: je hebt al ProgressDialogFragment in je project
    private void showProgressDialog(String title, String message) {
        androidx.fragment.app.FragmentManager fm = getSupportFragmentManager();
        ProgressDialogFragment old = (ProgressDialogFragment) fm.findFragmentByTag(TAG_PROGRESS);
        if (old != null) old.dismissAllowingStateLoss();
        ProgressDialogFragment f = ProgressDialogFragment.newInstance(title, message);
        f.show(fm, TAG_PROGRESS);
    }

    private void updateProgressUI(String phase, int cur, int total) {
        ProgressDialogFragment f = (ProgressDialogFragment) getSupportFragmentManager()
                .findFragmentByTag(TAG_PROGRESS);
        if (f != null) f.updateProgress(phase, cur, total);
    }

    private void hideProgressDialog() {
        ProgressDialogFragment f = (ProgressDialogFragment) getSupportFragmentManager()
                .findFragmentByTag(TAG_PROGRESS);
        if (f != null) f.dismissAllowingStateLoss();
    }
}
