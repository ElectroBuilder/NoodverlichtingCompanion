
package nl.mikekemmink.noodverlichting.nen3140;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.ui.BaseToolbarActivity;
import nl.mikekemmink.noodverlichting.nen3140.adapters.NenBoardListAdapter;

public class NenBoardsActivity extends BaseToolbarActivity {
    private NenStorage storage;
    private String locationId;
    private NenBoardListAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.activity_list_with_fab);
        setTitle("Verdeelinrichtingen");
        applyPalette(Palette.NEN);
        setUpEnabled(true);

        storage = new NenStorage(this);
        locationId = getIntent().getStringExtra("locationId");

        RecyclerView rv = findViewById(R.id.recyclerView);
        rv.setLayoutManager(new LinearLayoutManager(this));

        List<NenBoard> boards = storage.loadBoards(locationId);
        adapter = new NenBoardListAdapter(boards,
                board -> {
                    Intent i = new Intent(this, MeasurementsActivity.class);
                    i.putExtra("scope", "NEN");
                    i.putExtra("locationId", locationId);
                    i.putExtra("boardId", board.getId());
                    i.putExtra("prefillKastnaam", board.getName());
                    i.putExtra("lockKastnaam", true);
                    startActivity(i);
                },
                board -> {
                    // Long-press: beheer gebreken + verwijderen
                    String[] actions = new String[] { "Gebreken beheren", "Verwijderen (kast + meting)" };
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle(board.getName())
                            .setItems(actions, (d, which) -> {
                                switch (which) {
                                    case 0:
                                        Intent di = new Intent(this, DefectsActivity.class);
                                        di.putExtra("locationId", locationId);
                                        di.putExtra("boardId", board.getId());
                                        startActivity(di);
                                        break;
                                    case 1:
                                        new androidx.appcompat.app.AlertDialog.Builder(this)
                                                .setTitle("Verwijderen?")
                                                .setMessage("Kast \"" + board.getName() + "\" en ALLE metingen verwijderen? Dit kan niet ongedaan gemaakt worden.")
                                                .setPositiveButton("Verwijderen", (dd, w) -> {
                                                    storage.deleteBoard(locationId, board.getId());
                                                    adapter.setItems(storage.loadBoards(locationId));
                                                    Toast.makeText(this, "Kast en meting verwijderd", Toast.LENGTH_SHORT).show();
                                                })
                                                .setNegativeButton(android.R.string.cancel, null)
                                                .show();
                                        break;
                                }
                            })
                            .show();
                }
        );
        rv.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> showAddBoardDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (locationId != null) {
            adapter.setItems(storage.loadBoards(locationId));
        }
    }

    private void showAddBoardDialog() {
        final EditText input = new EditText(this);
        input.setHint("Kastnaam (bijv. H1-01)");
        new AlertDialog.Builder(this)
                .setTitle("Verdeelinrichting toevoegen")
                .setView(input)
                .setPositiveButton("Opslaan", (d, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        storage.addBoard(locationId, name);
                        adapter.setItems(storage.loadBoards(locationId));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
