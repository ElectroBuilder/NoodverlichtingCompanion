package nl.mikekemmink.noodverlichting.ui;

import android.os.Bundle;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.nen3140.NenLocation;
import nl.mikekemmink.noodverlichting.nen3140.NenStorage;
import nl.mikekemmink.noodverlichting.ui.adapters.NenLocationListAdapter;

public class NenLocationListActivity extends BaseToolbarActivity {
    private NenStorage storage;
    private NenLocationListAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.activity_list_with_fab);
        setTitle("Locaties (NEN3140)");
        applyPalette(Palette.NEN);
        setUpEnabled(true);

        storage = new NenStorage(this);
        List<NenLocation> locations = storage.loadLocations();

        RecyclerView rv = findViewById(R.id.recyclerView);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NenLocationListAdapter(locations,
                loc -> {
                    android.content.Intent i = new android.content.Intent(this, NenBoardsActivity.class);
                    i.putExtra("locationId", loc.getId());
                    startActivity(i);
                },
                loc -> {
                    storage.deleteLocation(loc.getId());
                    adapter.setItems(storage.loadLocations());
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
}
