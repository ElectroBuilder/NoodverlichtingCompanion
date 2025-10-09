package nl.mikekemmink.noodverlichting.nen3140;

import android.os.Bundle;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.ui.BaseToolbarActivity;
import nl.mikekemmink.noodverlichting.nen3140.adapters.NenLocationListAdapter;

public class NenLocationListActivity extends BaseToolbarActivity {
    private NenStorage storage;
    private NenLocationListAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.nen_list_with_fab);
        setTitle("Locaties (NEN3140)");
        applyPalette(Palette.NEN);
        setUpEnabled(true);

        storage = new NenStorage(this);
        List<NenLocation> locations = storage.loadLocations();

        RecyclerView rv = findViewById(R.id.recyclerView);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NenLocationListAdapter(
                locations,
                loc -> { // korte klik: naar bordenlijst
                    android.content.Intent i = new android.content.Intent(this, NenBoardsActivity.class);
                    i.putExtra("locationId", loc.getId());
                    startActivity(i);
                },
                loc -> { // lange klik: toon opties
                    showLocationOptionsDialog(loc);
                }
        );
        rv.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> showAddLocationDialog());
    }

    private void showAddLocationDialog() {
        final EditText input = new EditText(this);
        new AlertDialog.Builder(this)
                .setTitle("Locatie toevoegen")
                .setView(input)
                .setPositiveButton("Opslaan", (d, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        storage.addLocation(name);
                        adapter.setItems(storage.loadLocations());
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
    private void showLocationOptionsDialog(NenLocation loc) {
        String[] items = new String[]{"Bewerken", "Verwijderen"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(loc.getName())
                .setItems(items, (d, which) -> {
                    if (which == 0) showRenameLocationDialog(loc);
                    else if (which == 1) confirmDeleteLocation(loc);
                })
                .show();
    }

    private void showRenameLocationDialog(NenLocation loc) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setText(loc.getName());
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Locatie bewerken")
                .setView(input)
                .setPositiveButton("Opslaan", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        new NenStorage(this).updateLocationName(loc.getId(), name);
                        adapter.setItems(new NenStorage(this).loadLocations());
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDeleteLocation(NenLocation loc) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Verwijderen?")
                .setMessage("Locatie \"" + loc.getName() + "\" en alle bijbehorende borden, metingen, gebreken en foto’s worden verwijderd. Weet je het zeker?")
                .setPositiveButton("Verwijderen", (d, w) -> {
                    new NenStorage(this).deleteLocation(loc.getId());
                    adapter.setItems(new NenStorage(this).loadLocations());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

}
