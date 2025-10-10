
package nl.mikekemmink.noodverlichting.nen3140;

import android.Manifest;
import android.net.Uri;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.util.List;

import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.nen3140.adapters.NenDefectListAdapter;
import nl.mikekemmink.noodverlichting.ui.BaseToolbarActivity;

public class DefectsActivity extends BaseToolbarActivity {
    private NenStorage storage;
    private String locationId;
    private String boardId;
    private NenDefectListAdapter adapter;

    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private String pendingDefectId;
    private Uri pendingUri;

    private String pendingFileName;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.nen_locaties);
        setTitle("Gebreken");
        applyPalette(Palette.NEN);
        setUpEnabled(true);

        storage = new NenStorage(this);
        locationId = getIntent().getStringExtra("locationId");
        boardId = getIntent().getStringExtra("boardId");

        RecyclerView rv = findViewById(R.id.recyclerView);
        rv.setLayoutManager(new LinearLayoutManager(this));

        adapter = new NenDefectListAdapter(storage.loadDefects(locationId, boardId),
            defect -> showEditDefectDialog(defect.id, defect.text),
            defect -> confirmDeleteDefect(defect.id),
            defect -> startCameraForDefect(defect.id)
        );
        rv.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> showAddDefectDialog());

        // Launchers
        cameraPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) {
                if (pendingDefectId != null) launchCamera();
            } else {
                Toast.makeText(this, "Camera-toestemming geweigerd", Toast.LENGTH_LONG).show();
            }
        });

        takePictureLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            if (success && pendingUri != null && pendingDefectId != null) {
                // Sla bestandsnaam op (relatief t.o.v. photosDir)
                File photosDir = storage.getPhotosDir();
                File f = new File(pendingUri.getPath());
                // FileProvider geeft content://, path is niet direct bruikbaar -> reconstrueer via onze target File
                // We hebben 'pendingUri' aangemaakt op basis van een File in photosDir; sla dus die bestandsnaam op
                String fileName = new File(photosDir, new File(pendingUri.getLastPathSegment()).getName()).getName();
                // Fallback: als bovenstaande gek doet, gebruik vooraf berekende pendingFileName (via tag in Uri) -> simpeler: we bouwen zelf de filename bij createTempPhoto()
            }
            // Omdat het extraheren van de naam uit content:// tricky is, gebruiken we onze eigen helper die al de filename kent en bijhoudt.
            finishCamera(success);
        });
    }

    private void showAddDefectDialog() {
        final EditText input = new EditText(this);
        input.setHint("Omschrijving van het gebrek");
        new AlertDialog.Builder(this)
            .setTitle("Gebrek toevoegen")
            .setView(input)
            .setPositiveButton("Opslaan", (d, w) -> {
                String t = input.getText().toString().trim();
                NenDefect created = storage.addDefect(locationId, boardId, t.isEmpty() ? null : t);
                if (created != null) {
                    refresh();
                    // Optioneel: direct foto maken
                    startCameraForDefect(created.id);
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showEditDefectDialog(String defectId, String currentText) {
        final EditText input = new EditText(this);
        input.setHint("Omschrijving van het gebrek");
        if (currentText != null) input.setText(currentText);
        new AlertDialog.Builder(this)
            .setTitle("Gebrek bewerken")
            .setView(input)
            .setPositiveButton("Opslaan", (d, w) -> {
                String t = input.getText().toString().trim();
                storage.updateDefectText(locationId, boardId, defectId, t.isEmpty() ? null : t);
                refresh();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void confirmDeleteDefect(String defectId) {
        new AlertDialog.Builder(this)
            .setTitle("Verwijderen?")
            .setMessage("Dit gebrek en de gekoppelde foto verwijderen?")
            .setPositiveButton("Verwijderen", (d, w) -> {
                storage.deleteDefect(locationId, boardId, defectId, true);
                refresh();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void startCameraForDefect(String defectId) {
        this.pendingDefectId = defectId;
        // Check permission (sommige devices vereisen CAMERA ook bij TakePicture Intent)
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private void launchCamera() {
        try {
            File photosDir = storage.getPhotosDir();
            if (!photosDir.exists()) photosDir.mkdirs();
            String fileName = "defect_" + pendingDefectId + "_" + System.currentTimeMillis() + ".jpg";
            File out = new File(photosDir, fileName);
            pendingFileName = fileName; // <— bewaar direct
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", out);
            pendingUri = uri;
            takePictureLauncher.launch(uri);
        } catch (Exception e) {
            Toast.makeText(this, "Camera starten mislukt", Toast.LENGTH_LONG).show();
            this.pendingDefectId = null; this.pendingUri = null;
        }
    }

    private void finishCamera(boolean success) {
        if (success && pendingDefectId != null && pendingUri != null && pendingFileName != null) {
            storage.setDefectPhoto(locationId, boardId, pendingDefectId, pendingFileName);
            Toast.makeText(this, "Foto opgeslagen", Toast.LENGTH_SHORT).show();
            refresh();
        }
        pendingDefectId = null;
        pendingUri = null;
        pendingFileName = null;

    }

    private void refresh() {
        List<NenDefect> list = storage.loadDefects(locationId, boardId);
        adapter.setItems(list);
    }
}
