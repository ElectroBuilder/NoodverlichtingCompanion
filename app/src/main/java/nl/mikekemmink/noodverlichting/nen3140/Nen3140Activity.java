package nl.mikekemmink.noodverlichting.nen3140;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.io.File;

import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.nen3140.export.NenExporter;
import nl.mikekemmink.noodverlichting.ui.BaseToolbarActivity;

public class Nen3140Activity extends BaseToolbarActivity {
    private TextView txtInfo;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Zelfde layout als noodverlichting-beginscherm
        setContentLayout(R.layout.activity_main); // bevat txtInfo, btnStart, btnImportZip, btnExport
        setTitle("NEN3140");
        applyPalette(Palette.NEN);
        setUpEnabled(true);

        txtInfo = findViewById(R.id.txtInfo);
        Button btnStart = findViewById(R.id.btnStart);
        Button btnImportZip = findViewById(R.id.btnImportZip);
        Button btnExport = findViewById(R.id.btnExport);

        // START → locatiescherm (NEN-variant)
        btnStart.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, NenLocationListActivity.class))
        );

        // (Optioneel) placeholders voor import/export NEN3140
        btnImportZip.setOnClickListener(v -> txtInfo.setText("Import (NEN3140) nog niet geïmplementeerd"));
        btnExport.setOnClickListener(v -> {
            try {
                File zip = NenExporter.exportToZip(this, null);
                txtInfo.setText("NEN3140-export gereed:\n" + zip.getAbsolutePath());
            } catch (Exception e) {
                txtInfo.setText("Export mislukt: " + e.getMessage());
            }
        });


        txtInfo.setText("Klaar om te starten met NEN3140 (Locatie → Verdeelinrichting → Metingen)");
    }
}
