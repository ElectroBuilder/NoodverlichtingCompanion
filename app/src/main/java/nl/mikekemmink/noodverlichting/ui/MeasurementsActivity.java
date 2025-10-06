
// Replace package name below with your actual package
package nl.mikekemmink.noodverlichting.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.stroom.StroomRepo;
import nl.mikekemmink.noodverlichting.stroom.StroomWaardeEntry;
import android.widget.Toast;


public class MeasurementsActivity extends AppCompatActivity {

    // UI velden als classvelden
    private EditText etKastnaam, etL1, etL2, etL3, etN, etPE;

    // Edit-modus state
    private boolean isEdit = false;
    private String originalKastNaam = null;
    private String originalId = null;
    private Long originalTimestamp = null; // optioneel: als je oorspronkelijke tijd wilt bewaren



    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_measurements);

        etKastnaam = findViewById(R.id.et_kastnaam);

        etL1 = findViewById(R.id.et_l1);
        etL2 = findViewById(R.id.et_l2);
        etL3 = findViewById(R.id.et_l3);
        etN  = findViewById(R.id.et_n);
        etPE = findViewById(R.id.et_pe);


        // Edit-modus detecteren en velden prefillen
        String json = getIntent().getStringExtra("entry_json");
        if (json != null) {
            try {
                StroomWaardeEntry entry = StroomWaardeEntry.fromJson(new JSONObject(json));
                isEdit = true;
                originalKastNaam = entry.kastNaam;
                originalId = entry.id;
                originalTimestamp = entry.timestamp;

                // Prefill UI
                etKastnaam.setText(entry.kastNaam);
                etL1.setText(String.valueOf(entry.l1));
                etL2.setText(String.valueOf(entry.l2));
                etL3.setText(String.valueOf(entry.l3));
                etN.setText(String.valueOf(entry.n));
                etPE.setText(String.valueOf(entry.pe));

                // (optioneel) duidelijke titel
                // if (getSupportActionBar() != null) getSupportActionBar().setTitle("Meting bewerken");
            } catch (Exception ignore) { /* als parsen mislukt, ga gewoon in 'nieuw' modus */ }
        }


        Button btnCancel = findViewById(R.id.btn_cancel);
        Button btnSave   = findViewById(R.id.btn_save);

        btnCancel.setOnClickListener(v -> finish());

        btnSave.setOnClickListener(v -> {
            String kastnaam = etKastnaam.getText().toString().trim();
            if (TextUtils.isEmpty(kastnaam)) {
                etKastnaam.setError("Kastnaam is verplicht");
                etKastnaam.requestFocus();
                return;
            }

            Double l1D = parseNullableDouble(etL1.getText().toString());
            Double l2D = parseNullableDouble(etL2.getText().toString());
            Double l3D = parseNullableDouble(etL3.getText().toString());
            Double nD  = parseNullableDouble(etN.getText().toString());
            Double peD = parseNullableDouble(etPE.getText().toString());

            double l1 = l1D != null ? l1D : 0.0;
            double l2 = l2D != null ? l2D : 0.0;
            double l3 = l3D != null ? l3D : 0.0;
            double n  = nD  != null ? nD  : 0.0;
            double pe = peD != null ? peD : 0.0;

            // Zelfde id behouden bij bewerken; nieuw id bij aanmaken
            String id = isEdit && originalId != null
                    ? originalId
                    : java.util.UUID.randomUUID().toString();

            // Timestamp: update naar nu (wil je de oorspronkelijke tijd bewaren, gebruik dan originalTimestamp i.p.v. now)
            long ts = System.currentTimeMillis();
            // Als je de oorspronkelijke tijd wilt behouden bij edit, vervang de regel hierboven door:
            // long ts = (isEdit && originalTimestamp != null) ? originalTimestamp : System.currentTimeMillis();

            StroomWaardeEntry updated = new StroomWaardeEntry(
                    id, kastnaam, l1, l2, l3, n, pe, ts
            );

            // Als de kastnaam is gewijzigd: oude key verwijderen (anders blijft de oude naast de nieuwe bestaan)
            if (isEdit && originalKastNaam != null && !originalKastNaam.equals(kastnaam)) {
                StroomRepo.deleteByKast(this, originalKastNaam);
            }

            // Per kast opslaan/overschrijven
            StroomRepo.put(this, updated);

            // (Optioneel) jouw bestaande result-flow teruggeven:
            nl.mikekemmink.noodverlichting.ui.Measurement m =
                    new nl.mikekemmink.noodverlichting.ui.Measurement(
                            kastnaam, l1D, l2D, l3D, nD, peD
                    );

            Intent data = new Intent();
            data.putExtra("measurement", m);
            setResult(RESULT_OK, data);

            Toast.makeText(this, isEdit ? "Meting bijgewerkt" : "Meting opgeslagen", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private @Nullable Double parseNullableDouble(String raw) {
        if (TextUtils.isEmpty(raw)) return null;
        try {
            String normalized = raw.replace(',', '.');
            return Double.parseDouble(normalized);
        } catch (NumberFormatException ex) {
            Toast.makeText(this, "Ongeldig getal: " + raw, Toast.LENGTH_SHORT).show();
            return null;
        }
    }
}
