package nl.mikekemmink.noodverlichting;

import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import nl.mikekemmink.noodverlichting.data.DBField;
import nl.mikekemmink.noodverlichting.data.DBInspecties;
import nl.mikekemmink.noodverlichting.export.ExportHelper;

public class MainActivity extends BaseActivity {
    private TextView txtInfo;

    private final ActivityResultLauncher<String> pickDb = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    importInspectiesDb(uri);
                }
            }
    );


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_with_toolbar);

        MaterialToolbar tb = findViewById(R.id.toolbar);
        attachToolbar(tb);

        // ⬇️ voeg de content toe binnen de container van activity_with_toolbar
        getLayoutInflater().inflate(
            R.layout.content_main,
            findViewById(R.id.content_container),
            true

        );

        // Vanaf hier bestaan de views in de view‑hierarchy
        txtInfo          = findViewById(R.id.txtInfo);
        Button btnImport = findViewById(R.id.btnImport);
        Button btnStart  = findViewById(R.id.btnStart);
        Button btnExport = findViewById(R.id.btnExport);

        btnImport.setOnClickListener(v -> pickDb.launch("application/octet-stream"));
        btnStart.setOnClickListener(v ->
                startActivity(new Intent(this, nl.mikekemmink.noodverlichting.ui.LocationListActivity.class))
        );
        btnExport.setOnClickListener(v -> doExport());

        // DB-init
        DBField.getInstance(this).getWritableDatabase();

        // Inspecties proberen te openen
        SQLiteDatabase db = DBInspecties.tryOpenReadOnly(this);
        if (db != null) {
            txtInfo.setText("Status: inspecties.db geladen");
            db.close();
        } else {
            txtInfo.setText("Status: importeer eerst inspecties.db");
        }
    }


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
        } catch (Exception e) {
            txtInfo.setText("Import mislukt: " + e.getMessage());
        }
    }

    private void doExport() {
        try {
            String zipPath = ExportHelper.exportToZip(this);
            txtInfo.setText("Geëxporteerd: " + zipPath);
        } catch (Exception e) {
            txtInfo.setText("Export mislukt: " + e.getMessage());
        }
    }
}