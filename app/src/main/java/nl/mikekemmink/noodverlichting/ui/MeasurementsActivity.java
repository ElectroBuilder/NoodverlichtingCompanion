package nl.mikekemmink.noodverlichting.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.nen3140.StroomRepo;
import nl.mikekemmink.noodverlichting.nen3140.StroomWaardeEntry;

// ▼ Voeg toe als je de NEN-storage reeds in je project hebt (uit de bundel)
import nl.mikekemmink.noodverlichting.nen3140.NenStorage;
import nl.mikekemmink.noodverlichting.nen3140.NenMeasurement;

public class MeasurementsActivity extends BaseToolbarActivity {

    // UI
    private EditText etKastnaam, etL1, etL2, etL3, etN, etPE;

    // Edit-modus (legacy)
    private boolean isEdit = false;
    private String originalKastNaam = null;
    private String originalId = null;
    private Long originalTimestamp = null;

    // Context (NEN)
    private String scope;        // "NEN" of null (legacy)
    private String locationId;   // bij NEN
    private String boardId;      // bij NEN
    private String nenMeasurementId; // ✅ nieuw


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.activity_measurements);
        applyPalette(Palette.NEN);
        setUpEnabled(true);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle(R.string.title_measurements);

        // bind views
        etKastnaam = findViewById(R.id.et_kastnaam);
        etL1 = findViewById(R.id.et_l1);
        etL2 = findViewById(R.id.et_l2);
        etL3 = findViewById(R.id.et_l3);
        etN  = findViewById(R.id.et_n);
        etPE = findViewById(R.id.et_pe);

        // ✅ extras NA findViewById
        scope = getIntent().getStringExtra("scope");
        locationId = getIntent().getStringExtra("locationId");
        boardId = getIntent().getStringExtra("boardId");
        nenMeasurementId = getIntent().getStringExtra("nenMeasurementId"); // ✅ nieuw
        String prefillKast = getIntent().getStringExtra("prefillKastnaam");
        boolean lockKast = getIntent().getBooleanExtra("lockKastnaam", false);

        if (prefillKast != null) etKastnaam.setText(prefillKast);
        if (lockKast) {etKastnaam.setEnabled(false);etKastnaam.setFocusable(false);}

        // 3) Legacy: entry_json = bewerken
        String json = getIntent().getStringExtra("entry_json");
        if (json != null) {
            try {
                StroomWaardeEntry entry = StroomWaardeEntry.fromJson(new JSONObject(json));
                isEdit = true;
                originalKastNaam = entry.kastNaam;
                originalId = entry.id;
                originalTimestamp = entry.timestamp;

                etKastnaam.setText(entry.kastNaam);
                etL1.setText(String.valueOf(entry.l1));
                etL2.setText(String.valueOf(entry.l2));
                etL3.setText(String.valueOf(entry.l3));
                etN.setText(String.valueOf(entry.n));
                etPE.setText(String.valueOf(entry.pe));
            } catch (Exception ignore) {
                // Ga door als 'nieuw'
            }

            // ✅ NEN edit: prefill direct uit NenStorage als nenMeasurementId is gezet
            if ("NEN".equals(scope) && nenMeasurementId != null) {
                NenStorage nen = new NenStorage(this);
                NenMeasurement em = nen.getMeasurement(locationId, boardId, nenMeasurementId);
                if (em != null) {
                    // Etiket/naam van de kast staat al in prefill/lock; hier de waardes
                    etL1.setText(String.valueOf(em.L1));
                    etL2.setText(String.valueOf(em.L2));
                    etL3.setText(String.valueOf(em.L3));
                    etN.setText(String.valueOf(em.N));
                    etPE.setText(String.valueOf(em.PE));
                }
            }

        }

        Button btnCancel = findViewById(R.id.btn_cancel);
        Button btnSave   = findViewById(R.id.btn_save);

        btnCancel.setOnClickListener(v -> finish());

        btnSave.setOnClickListener(v -> {
            String kastnaam = etKastnaam.getText().toString().trim();
            if (TextUtils.isEmpty(kastnaam)) {
                etKastnaam.setError(getString(R.string.error_required));
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

            String id = (isEdit && originalId != null) ? originalId : java.util.UUID.randomUUID().toString();
            long ts = System.currentTimeMillis();

            // Altijd een Measurement als result teruggeven (voor eventuele call-site die dat wil)
            Measurement m = new Measurement(kastnaam, l1D, l2D, l3D, nD, peD);
            Intent data = new Intent();
            data.putExtra("measurement", m);
            setResult(RESULT_OK, data);

            if ("NEN".equals(scope)) {
                // ▼ NEN: losstaande opslag in JSON via NenStorage
                NenStorage nen = new NenStorage(this);

// id = null → NenStorage.addMeasurement(...) geeft zelf een UUID
                NenMeasurement nm = new NenMeasurement(null, l1, l2, l3, n, pe, ts);

                nen.addMeasurement(locationId, boardId, nm);

                // (Optioneel) dual-write naar legacy overzicht/CSV:
                // StroomRepo.put(this, new StroomWaardeEntry(id, kastnaam, l1, l2, l3, n, pe, ts));
                Toast.makeText(this, R.string.msg_measurement_saved, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            // ▼ Legacy: bestaand gedrag behouden (StroomRepo)
            StroomWaardeEntry updated = new StroomWaardeEntry(id, kastnaam, l1, l2, l3, n, pe, ts);

            // Alleen bij legacy: oude key opruimen wanneer kastnaam wijzigt
            if (isEdit && originalKastNaam != null && !originalKastNaam.equals(kastnaam)) {
                StroomRepo.deleteByKast(this, originalKastNaam);
            }

            StroomRepo.put(this, updated);
            Toast.makeText(this, isEdit ? R.string.msg_measurement_updated : R.string.msg_measurement_saved, Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    @Override
    protected int getActivityToolbarMenuRes() {
        return 0;
    }

    private @Nullable Double parseNullableDouble(String raw) {
        if (TextUtils.isEmpty(raw)) return null;
        try {
            String normalized = raw.replace(',', '.');
            return Double.parseDouble(normalized);
        } catch (NumberFormatException ex) {
            Toast.makeText(this, getString(R.string.error_invalid_number, raw), Toast.LENGTH_SHORT).show();
            return null;
        }
    }
}