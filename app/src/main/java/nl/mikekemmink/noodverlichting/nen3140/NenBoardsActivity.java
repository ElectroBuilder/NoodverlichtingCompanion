package nl.mikekemmink.noodverlichting.nen3140;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.nen3140.adapters.NenBoardListAdapter;
import nl.mikekemmink.noodverlichting.ui.BaseToolbarActivity;
import nl.mikekemmink.noodverlichting.views.PdfMapView;

public class NenBoardsActivity extends BaseToolbarActivity {
    private NenStorage storage;
    private String locationId;
    private NenBoardListAdapter adapter;

    private static final int REQ_OPEN_PDF = 101;
    private File currentPdfFile;
    private PdfMapView pdfMapView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1) Layout & toolbar eerst
        setContentLayout(R.layout.nen_boards_with_viewer);
        setTitle("Verdeelinrichtingen");
        applyPalette(Palette.NEN);
        setUpEnabled(true);

        // 2) Data
        storage = new NenStorage(this);
        locationId = getIntent().getStringExtra("locationId");

        // 3) Lijst met borden
        RecyclerView rv = findViewById(R.id.recyclerView);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NenBoardListAdapter(
                storage.loadBoards(locationId),
                locationId,
                storage,
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
                    String[] actions = new String[] { "Bewerken", "Gebreken beheren", "Verwijderen (kast + meting)" };
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle(board.getName())
                            .setItems(actions, (d, which) -> {
                                switch (which) {
                                    case 0:
                                        showRenameBoardDialog(board);
                                        break;
                                    case 1:
                                        Intent di = new Intent(this, DefectsActivity.class);
                                        di.putExtra("locationId", locationId);
                                        di.putExtra("boardId", board.getId());
                                        startActivity(di);
                                        break;
                                    case 2:
                                        new androidx.appcompat.app.AlertDialog.Builder(this)
                                                .setTitle("Verwijderen?")
                                                .setMessage("Kast \"" + board.getName() + "\" en ALLE metingen verwijderen? Dit kan niet ongedaan gemaakt worden.")
                                                .setPositiveButton("Verwijderen", (dd, w) -> {
                                                    storage.deleteBoard(locationId, board.getId());
                                                    adapter.setItems(storage.loadBoards(locationId));
                                                    android.widget.Toast.makeText(this, "Kast en meting verwijderd", android.widget.Toast.LENGTH_SHORT).show();
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

        // 4) PDF viewer + knop
        pdfMapView = findViewById(R.id.pdfMapView);
        pdfMapView.setLocationId(locationId);

        View btnOpen = findViewById(R.id.btnOpenPdf);
        if (btnOpen != null) btnOpen.setOnClickListener(v -> openPdfPicker());

        // 5) Herstel automatisch de laatst geopende PDF + pagina
        boolean restored = pdfMapView.tryOpenLastPdfForLocation();
        if (restored) {
            setupPageSpinner(); // spinner syncen met huidige pagina
        }

        // 6) FAB voor kast toevoegen
        com.google.android.material.floatingactionbutton.FloatingActionButton fab = findViewById(R.id.fab);
        if (fab != null) fab.setOnClickListener(v -> showAddBoardDialog());
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
        new androidx.appcompat.app.AlertDialog.Builder(this)
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

    private void showRenameBoardDialog(NenBoard board) {
        final EditText input = new EditText(this);
        input.setText(board.getName());
        input.setSelection(input.getText().length());
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Kastnaam bewerken")
                .setView(input)
                .setPositiveButton("Opslaan", (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        storage.updateBoardName(locationId, board.getId(), newName);
                        adapter.setItems(storage.loadBoards(locationId));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openPdfPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        startActivityForResult(intent, REQ_OPEN_PDF);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OPEN_PDF && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                currentPdfFile = copyPdfToAppStorage(uri);
                if (currentPdfFile != null) {
                    pdfMapView.openPdf(currentPdfFile);
                    setupPageSpinner();
                }
            }
        }
    }

    private void setupPageSpinner() {
        Spinner sp = findViewById(R.id.spPage);
        int count = pdfMapView.getPageCount();
        List<String> pages = new ArrayList<>();
        for (int i = 0; i < count; i++) pages.add("Pagina " + (i + 1));

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, pages);
        sp.setAdapter(adapter);

        final int current = pdfMapView.getCurrentPageIndex(); // nieuwe getter in PdfMapView
        final boolean[] ignoreFirst = { true };

        sp.setSelection(current, false);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (ignoreFirst[0]) { ignoreFirst[0] = false; return; }
                if (pos != pdfMapView.getCurrentPageIndex()) pdfMapView.setPageIndex(pos);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

    }

    private File copyPdfToAppStorage(Uri uri) {
        File dir = new File(getFilesDir(), "plattegronden");
        if (!dir.exists()) dir.mkdirs();
        String name = queryDisplayName(uri);
        if (name == null || !name.toLowerCase().endsWith(".pdf")) {
            name = "plattegrond_" + System.currentTimeMillis() + ".pdf";
        }
        File out = new File(dir, name);
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) os.write(buf, 0, len);
            os.flush();
            return out;
        } catch (Exception e) {
            e.printStackTrace();
            android.widget.Toast.makeText(this, "Kopiëren mislukt: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
            return null;
        }
    }

    private @Nullable String queryDisplayName(Uri uri) {
        Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
        if (c != null) {
            try {
                if (c.moveToFirst()) return c.getString(0);
            } finally {
                c.close();
            }
        }
        return null;
    }
}