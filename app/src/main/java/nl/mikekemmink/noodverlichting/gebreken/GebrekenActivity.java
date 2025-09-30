package nl.mikekemmink.noodverlichting.ui.gebreken;

import android.os.Bundle;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import nl.mikekemmink.noodverlichting.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.List;

public class GebrekenActivity extends AppCompatActivity implements EditGebrekDialog.OnSaveListener {

    private RecyclerView recycler;
    private GebrekenAdapter adapter;
    private final List<Gebrek> data = new ArrayList<>();
    private Gebrek laatstVerwijderd;
    private int laatstVerwijderdPos = -1;
    private int editingPos = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gebreken);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // Back icoon + gedrag
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());

        recycler = findViewById(R.id.recyclerGebreken);
        adapter = new GebrekenAdapter(data, (g, pos) -> openEditDialog(g, pos));
        recycler.setAdapter(adapter);

        // TODO: Laad daadwerkelijke data uit DB op basis van meegegeven inspectie_id
        // long inspectieId = getIntent().getLongExtra("inspectie_id", -1);
        seedDemoData();

        ItemTouchHelper ith = new ItemTouchHelper(new SwipeCallback(this,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                int pos = vh.getAdapterPosition();
                if (direction == ItemTouchHelper.LEFT) {
                    // Verwijderen met UNDO
                    laatstVerwijderd = data.get(pos);
                    laatstVerwijderdPos = pos;
                    data.remove(pos);
                    adapter.notifyItemRemoved(pos);
                    Snackbar.make(recycler, "Gebrek verwijderd", Snackbar.LENGTH_LONG)
                            .setAction("UNDO", v -> {
                                if (laatstVerwijderd != null && laatstVerwijderdPos >= 0 && laatstVerwijderdPos <= data.size()) {
                                    data.add(laatstVerwijderdPos, laatstVerwijderd);
                                    adapter.notifyItemInserted(laatstVerwijderdPos);
                                }
                            })
                            .addCallback(new Snackbar.Callback() {
                                @Override public void onDismissed(Snackbar bar, int event) {
                                    if (event != DISMISS_EVENT_ACTION && laatstVerwijderd != null) {
                                        // TODO: Definitief verwijderen in DB
                                        laatstVerwijderd = null;
                                        laatstVerwijderdPos = -1;
                                    }
                                }
                            })
                            .show();
                } else if (direction == ItemTouchHelper.RIGHT) {
                    Gebrek g = data.get(pos);
                    adapter.notifyItemChanged(pos); // reset swipe UI
                    openEditDialog(g, pos);
                }
            }
        });
        ith.attachToRecyclerView(recycler);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { finish(); }
        });
    }

    private void openEditDialog(Gebrek g, int position) {
        editingPos = position;
        EditGebrekDialog.newInstance(g.omschrijving)
                .show(getSupportFragmentManager(), "edit");
    }

    @Override
    public void onSave(String nieuweOmschrijving) {
        if (editingPos >= 0 && editingPos < data.size()) {
            Gebrek g = data.get(editingPos);
            g.omschrijving = nieuweOmschrijving;
            // TODO: Persistente update in DB
            adapter.notifyItemChanged(editingPos);
        }
        editingPos = -1;
    }

    private void seedDemoData() {
        // Enkel voor demo / testen van UI. Vervang met echte data uit DB.
        for (int i = 1; i <= 8; i++) {
            Gebrek g = new Gebrek();
            g.id = i;
            g.inspectieId = 123;
            g.omschrijving = "Gebrek #" + i + " (voorbeeld)";
            g.datum = "2025-09-29";
            data.add(g);
        }
    }
}
