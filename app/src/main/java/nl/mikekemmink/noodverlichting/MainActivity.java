package nl.mikekemmink.noodverlichting;

import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.shockwave.pdfium.BuildConfig;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import nl.mikekemmink.noodverlichting.data.DBField;
import nl.mikekemmink.noodverlichting.data.DBInspecties;
import nl.mikekemmink.noodverlichting.export.ExportHelper;
import nl.mikekemmink.noodverlichting.ui.Measurement;

public class MainActivity extends AppCompatActivity {

    private TextView txtInfo;

    // Onthoud het laatst gecreëerde exportpad om direct te kunnen importeren zonder picker
    private @Nullable String lastExportPath = null;

    // Persistente voorkeuren
    private static final String PREFS = "app_prefs";
    private static final String KEY_DATA_IMPORTED = "data_imported";
    private static final String KEY_LAST_EXPORT_PATH = "last_export_path";

    // Optionele helpers
    private void openSettings() {
        // TODO: startActivity(new Intent(this, SettingsActivity.class));
    }

    private void openHelp() {
        // TODO: open help-URL of start HelpActivity
    }
    // Opent MeasurementsActivity en ontvangt (optioneel) een resultaat terug
    private final ActivityResultLauncher<Intent> measurementsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Measurement m = (Measurement) result.getData().getSerializableExtra("measurement");
                    if (m != null) {
                        Toast.makeText(this, "Ontvangen: " + m.getKastnaam(), Toast.LENGTH_SHORT).show();
                    }
                }
            });
    private void showAboutDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Over")
                .setMessage(getString(R.string.app_name) + "\nVersie " + BuildConfig.VERSION_NAME)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    // Losse DB-import (handmatig .db/.sqlite kiezen)
    private final ActivityResultLauncher<String> pickDb =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> { if (uri != null) importInspectiesDb(uri); }
            );

    // ZIP-picker voor het complete exportpakket (fallback wanneer lastExportPath niet bruikbaar is)
    private final ActivityResultLauncher<String[]> pickZip =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    // Best-effort: persisteer leesrecht (kan op sommige devices SecurityException gooien)
                    try {
                        getContentResolver().takePersistableUriPermission(
                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        );
                    } catch (Exception ignore) {}
                    importZipFromUri(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_settings) {
                openSettings();
                return true;
            } else if (id == R.id.action_about) {
                showAboutDialog();
                return true;
            } else if (id == R.id.action_help) {
                openHelp();
                return true;
            }
            return false;
        });

        Button btnMeasurements = findViewById(R.id.btn_open_measurements);
        btnMeasurements.setOnClickListener(v -> {
            // Debug-toast: zie je deze, dan werkt de klik
            Toast.makeText(this, "Stroomwaarden openen…", Toast.LENGTH_SHORT).show();

            // Volledige gekwalificeerde classnaam gebruiken is het meest expliciet:
            Intent intent = new Intent(
                    MainActivity.this,
                    nl.mikekemmink.noodverlichting.ui.MeasurementsActivity.class
            );

            // Gebruik óf de Activity Result API...
            measurementsLauncher.launch(intent);

            // ...of tijdelijk startActivity(intent) om snel uit te sluiten dat het aan de Result API ligt:
            // startActivity(intent);
        });


    txtInfo = findViewById(R.id.txtInfo);
        Button btnImportZip = findViewById(R.id.btnImportZip);
        //Button btnImport = findViewById(R.id.btnImport);
        Button btnStart = findViewById(R.id.btnStart);
        Button btnExport = findViewById(R.id.btnExport);

        // 1) Exportpakket importeren (korte tap = laatste export proberen, anders picker)
        btnImportZip.setOnClickListener(v -> importLastExportOrPick());
        // Long-press = altijd handmatig ZIP kiezen
        btnImportZip.setOnLongClickListener(v -> {
            pickZip.launch(new String[]{"application/zip", "application/octet-stream"});
            return true;
        });

        // 3) Start hoofdfunctionaliteit
        btnStart.setOnClickListener(v ->
                startActivity(new Intent(this, nl.mikekemmink.noodverlichting.ui.LocationListActivity.class))
        );

        // 4) Export
        btnExport.setOnClickListener(v -> doExport());

        // DB-init
        DBField.getInstance(this).getWritableDatabase();

        // Prefs + laatst bekend pad herstellen
        lastExportPath = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_LAST_EXPORT_PATH, null);

        // Bepaal of dataset aanwezig is (blijft bewaard tussen sessies)
        if (isDatasetPresent()) {
            txtInfo.setText("Status: dataset gevonden – je kunt direct starten");
        } else {
            // Inspecties proberen te openen voor status
            SQLiteDatabase db = DBInspecties.tryOpenReadOnly(this);
            if (db != null) {
                txtInfo.setText("Status: inspecties.db geladen");
                db.close();
            } else {
                txtInfo.setText("Status: importeer eerst inspecties.db of een exportpakket");
            }
        }
    }

    /** Controleer of de geïmporteerde dataset (DB + markers + ten minste één PDF) aanwezig is. */
    private boolean isDatasetPresent() {
        File db = getDatabasePath("inspecties.db");
        if (db == null || !db.exists() || db.length() == 0) return false;

        File markers = new File(getFilesDir(), "markers/markers.json");
        File pdfDir = new File(getFilesDir(), "plattegronden");

        boolean hasMarkers = markers.exists() && markers.length() > 0;
        String[] pdfs = (pdfDir.exists() && pdfDir.isDirectory())
                ? pdfDir.list((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".pdf"))
                : null;
        boolean hasAtLeastOnePdf = pdfs != null && pdfs.length > 0;

        // In jouw flow is DB + markers + ≥1 PDF representatief voor "compleet".
        return hasMarkers && hasAtLeastOnePdf;
    }

    /* =========================
       EXPORT
       ========================= */
    private void doExport() {
        try {
            String zipPath = ExportHelper.exportToZip(this);
            lastExportPath = zipPath; // <-- onthouden voor snelle “laatste export importeren”
            txtInfo.setText("Geëxporteerd: " + zipPath);
            Toast.makeText(this, "Export voltooid", Toast.LENGTH_SHORT).show();

            // Persist ‘lastExportPath’ zodat deze ook na app-herstart beschikbaar is
            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit().putString(KEY_LAST_EXPORT_PATH, zipPath).apply();
        } catch (Exception e) {
            txtInfo.setText("Export mislukt: " + e.getMessage());
            Toast.makeText(this, "Export mislukt", Toast.LENGTH_LONG).show();
        }
    }

    /* =========================
       IMPORT – losse DB (handmatig)
       ========================= */
    private void importInspectiesDb(Uri uri) {
        try {
            File dbDir = getDatabasePath("inspecties.db").getParentFile();
            if (!dbDir.exists()) dbDir.mkdirs();
            File dst = new File(dbDir, "inspecties.db");
            try (InputStream in = getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            txtInfo.setText("Inspecties DB geïmporteerd: " + dst.getAbsolutePath());
            Toast.makeText(this, "Database geïmporteerd", Toast.LENGTH_SHORT).show();

            // Markeer dataset als aanwezig (DB is in elk geval geplaatst)
            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit().putBoolean(KEY_DATA_IMPORTED, true).apply();
        } catch (Exception e) {
            txtInfo.setText("Import mislukt: " + e.getMessage());
            Toast.makeText(this, "DB-import mislukt", Toast.LENGTH_LONG).show();
        }
    }

    /* =========================
       IMPORT – gecombineerde ZIP (markers + PDF’s + DB)
       ========================= */
    private void importLastExportOrPick() {
        // 1) Probeer de laatst gemaakte export direct te importeren
        if (lastExportPath != null) {
            File f = new File(lastExportPath);
            if (f.exists() && f.isFile() && f.length() > 0) {
                try {
                    importZipFromFile(f);
                    return;
                } catch (Exception e) {
                    Log.w("IMPORT", "Laatste export importeren faalde: " + e.getMessage());
                    // val door naar picker
                }
            }
        }
        // 2) Fallback: laat gebruiker een ZIP kiezen
        pickZip.launch(new String[]{"application/zip", "application/octet-stream"});
    }

    private void importZipFromUri(@NonNull Uri zipUri) {
        try (InputStream raw = getContentResolver().openInputStream(zipUri)) {
            if (raw == null) throw new FileNotFoundException("Zip-inputstream is null");
            importZipFromStream(raw);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Fout bij importeren ZIP: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void importZipFromFile(@NonNull File zipFile) throws Exception {
        try (InputStream raw = new FileInputStream(zipFile)) {
            importZipFromStream(raw);
        }
    }

    /** Kern: lees ZIP-stream en importeer markers.json, *.pdf en *.sqlite/*.db */
    private void importZipFromStream(@NonNull InputStream raw) throws Exception {
        File pdfDir = new File(getFilesDir(), "plattegronden");
        File markersDir = new File(getFilesDir(), "markers");
        if (!pdfDir.exists()) pdfDir.mkdirs();
        if (!markersDir.exists()) markersDir.mkdirs();
        File markersOut = new File(markersDir, "markers.json");
        File dbOut = getDatabasePath("inspecties.db");
        int pdfCount = 0;
        boolean markersOK = false;
        boolean dbReplaced = false;

        // Schrijf DB eerst naar temp; vervang pas na volledig lezen
        File tempDb = null;
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(raw))) {
            ZipEntry entry;
            byte[] buf = new byte[64 * 1024];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                String name = entry.getName();
                String lower = name.toLowerCase(Locale.ROOT);
                if (lower.equals("markers.json")) {
                    // markers.json overschrijven
                    copyToFile(zis, markersOut, buf, true);
                    // Optioneel debug: inhoud lezen -- overgeslagen
                    try {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        baos.close();
                    } catch (Exception ignore) {}
                    markersOK = true;
                } else if (lower.endsWith(".pdf")) {
                    File out = new File(pdfDir, new File(name).getName());
                    copyToFile(zis, out, buf, true);
                    pdfCount++;
                } else if (lower.endsWith(".sqlite") || lower.endsWith(".db")) {
                    if (tempDb == null) tempDb = File.createTempFile("import_db_", ".sqlite", getCacheDir());
                    copyToFile(zis, tempDb, buf, true);
                }
                zis.closeEntry();
            }
        }

        // ==== DB veilig vervangen ====
        if (tempDb != null && tempDb.exists() && tempDb.length() > 0) {
            try {
                // Sluit eventuele open DB's best-effort
                try { SQLiteDatabase.releaseMemory(); } catch (Exception ignore) {}
                // Backup huidige DB (best effort)
                File backup = new File(dbOut.getParentFile(), "database_backup_before_import.sqlite");
                if (dbOut.exists()) {
                    copyFile(dbOut, backup);
                    if (!dbOut.delete()) {
                        Log.w("IMPORT", "Kon oude DB niet verwijderen; probeer overschrijven.");
                    }
                }
                // Plaats nieuwe DB
                ensureParent(dbOut);
                if (!tempDb.renameTo(dbOut)) {
                    copyFile(tempDb, dbOut);
                    //noinspection ResultOfMethodCallIgnored
                    tempDb.delete();
                }
                dbReplaced = true;
            } catch (Exception e) {
                throw new Exception("DB vervangen mislukt: " + e.getMessage(), e);
            }
        }

        // UI-feedback
        String summary = "Import klaar • DB: " + (dbReplaced ? "OK" : "—")
                + " • markers: " + (markersOK ? "OK" : "—")
                + " • PDF’s: " + pdfCount;
        txtInfo.setText(summary);
        Toast.makeText(this, "Exportpakket geïmporteerd", Toast.LENGTH_SHORT).show();

        // Persistente vlag zodat we bij herstart weten dat data aanwezig is
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().putBoolean(KEY_DATA_IMPORTED, true).apply();

        // TODO: hier evt. je ViewModels/Adapters opnieuw laden of Activity herstarten
    }

    /* ========== I/O helpers ========== */
    private static void ensureParent(@NonNull File f) {
        File p = f.getParentFile();
        if (p != null && !p.exists()) p.mkdirs();
    }

    private static void copyToFile(@NonNull InputStream in, @NonNull File out,
                                   @NonNull byte[] buf, boolean overwrite) throws Exception {
        ensureParent(out);
        if (out.exists() && !overwrite) return;
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(out, false))) {
            int r;
            while ((r = in.read(buf)) != -1) {
                bos.write(buf, 0, r);
            }
            bos.flush();
        }
    }

    private static void copyFile(@NonNull File src, @NonNull File dst) throws Exception {
        ensureParent(dst);
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(src));
             BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(dst))) {
            byte[] buf = new byte[64 * 1024];
            int r;
            while ((r = bis.read(buf)) != -1) {
                bos.write(buf, 0, r);
            }
            bos.flush();
        }
    }
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            openSettings();
            return true;
        } else if (id == R.id.action_about) {
            showAboutDialog();
            return true;
        } else if (id == R.id.action_help) {
            openHelp();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}