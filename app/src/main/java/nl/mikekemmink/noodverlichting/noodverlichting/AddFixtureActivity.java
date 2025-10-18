package nl.mikekemmink.noodverlichting.noodverlichting;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.noodverlichting.data.DBHelper;
import nl.mikekemmink.noodverlichting.ui.BaseToolbarActivity;

public class AddFixtureActivity extends BaseToolbarActivity {

    private static final String TAG = "AddFixtureActivity";
    private static final String TABLE_INSPECTIES = "inspecties";

    private EditText etLocatie, etNr, etCode, etType, etMerk, etPictogram,
            etAccuType, etArtikelNr, etAccuLeeftijd, etDuurtest, etOpmerking;
    private AutoCompleteTextView acVerdieping, acRuimte;
    private Spinner spSoort, spMontage;
    private CheckBox cbOpTekening, cbATS;

    private DBHelper dbHelper;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.activity_add_fixture);
        applyPalette(Palette.NOOD);
        setUpEnabled(true);
        setTitle("Armatuur toevoegen");

        dbHelper = new DBHelper(this);

        // Views
        etLocatie       = findViewById(R.id.etLocatie);
        etNr            = findViewById(R.id.etNr);
        etCode          = findViewById(R.id.etCode);
        spSoort         = findViewById(R.id.spSoort);
        acVerdieping    = findViewById(R.id.etVerdieping);
        acRuimte        = findViewById(R.id.etRuimte);
        cbOpTekening    = findViewById(R.id.cbOpTekening);
        etType          = findViewById(R.id.etType);
        etMerk          = findViewById(R.id.etMerk);
        spMontage       = findViewById(R.id.spMontage);
        etPictogram     = findViewById(R.id.etPictogram);
        etAccuType      = findViewById(R.id.etAccuType);
        etArtikelNr     = findViewById(R.id.etArtikelNr);
        etAccuLeeftijd  = findViewById(R.id.etAccuLeeftijd);
        cbATS           = findViewById(R.id.cbATS);
        etDuurtest      = findViewById(R.id.etDuurtest);
        etOpmerking     = findViewById(R.id.etOpmerking);

        // Soort
        ArrayAdapter<CharSequence> soortAdapter = ArrayAdapter.createFromResource(
                this, R.array.soort_values, android.R.layout.simple_spinner_item);
        soortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spSoort.setAdapter(soortAdapter);

        // Montagewijze
        ArrayAdapter<String> montageAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item,
                new String[]{"", "Opbouw", "Inbouw", "Wand", "Plafond", "Hangend"});
        montageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spMontage.setAdapter(montageAdapter);

        // Locatie (read-only indien meegegeven)
        String locatie = getIntent() != null
                ? getIntent().getStringExtra(LocationListActivity.EXTRA_LOCATIE)
                : null;
        if (!TextUtils.isEmpty(locatie)) {
            etLocatie.setText(locatie);
            etLocatie.setEnabled(false);
        }

        // Suggesties Verdieping/Ruimte
        loadSuggestionsForLocation();
        acVerdieping.setThreshold(0);
        acRuimte.setThreshold(0);
        acVerdieping.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) acVerdieping.showDropDown(); });
        acRuimte.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) acRuimte.showDropDown(); });

        findViewById(R.id.btnOpslaan).setOnClickListener(v -> saveAndFinish());
        findViewById(R.id.btnAnnuleren).setOnClickListener(v -> finish());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void saveAndFinish() {
        // Lees waarden (geen verplichte velden)
        String locatie     = nullIfEmpty(etLocatie.getText().toString());
        String code        = nullIfEmpty(etCode.getText().toString());
        String nr          = nullIfEmpty(etNr.getText().toString());
        String soort       = spSoort.getSelectedItem() != null
                ? nullIfEmpty(spSoort.getSelectedItem().toString()) : null;
        String verdieping  = nullIfEmpty(acVerdieping.getText().toString());
        String ruimte      = nullIfEmpty(acRuimte.getText().toString());
        String opTekening  = cbOpTekening.isChecked() ? "Ja" : "Nee";
        String type        = nullIfEmpty(etType.getText().toString());
        String merk        = nullIfEmpty(etMerk.getText().toString());
        String montage     = spMontage.getSelectedItem() != null
                ? nullIfEmpty(spMontage.getSelectedItem().toString()) : null;
        String pictogram   = nullIfEmpty(etPictogram.getText().toString());
        String accuType    = nullIfEmpty(etAccuType.getText().toString());
        String artikelNr   = nullIfEmpty(etArtikelNr.getText().toString());
        String accuLeeftijd= nullIfEmpty(etAccuLeeftijd.getText().toString());
        String ats         = cbATS.isChecked() ? "Ja" : "Nee";
        String duurtest    = nullIfEmpty(etDuurtest.getText().toString());
        String opmerking   = nullIfEmpty(etOpmerking.getText().toString());

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Set<String> cols = getColumns(db, TABLE_INSPECTIES);

        // Dynamische kolomkeuze (zoals bij jou), maar we bewaren ze in lijsten
        List<String> insertCols = new ArrayList<>();
        List<Object> bindArgs   = new ArrayList<>();

        addIfExists(cols, insertCols, bindArgs, new String[]{"Locatie","locatie","Location","location"}, locatie);
        addIfExists(cols, insertCols, bindArgs, new String[]{"Nr.","Nr","nr.","nr","Nummer","nummer","ArmatuurNr","armatuur_nr","armatuurnr"}, nr);
        addIfExists(cols, insertCols, bindArgs, new String[]{"Code","code","ArmatuurCode","armatuurcode","arm_code"}, code);
        addIfExists(cols, insertCols, bindArgs, new String[]{"Soort","soort","Type","type"}, soort);
        addIfExists(cols, insertCols, bindArgs, new String[]{"Verdieping","verdieping","Floor","floor","Etage","etage"}, verdieping);
        addIfExists(cols, insertCols, bindArgs, new String[]{"Ruimte","ruimte","Room","room","Kamer","kamer"}, ruimte);
        addIfExists(cols, insertCols, bindArgs, new String[]{"Op tekening","Op_Tekening","OpTekening","op_tekening","optekening"}, opTekening);
        addIfExists(cols, insertCols, bindArgs, new String[]{"Type","type","Armatuur type"}, type);
        addIfExists(cols, insertCols, bindArgs, new String[]{"Merk","merk","Armatuur merk","armatuur_merk","ArmatuurMerk"}, merk);
        addIfExists(cols, insertCols, bindArgs, new String[]{"Montagewijze","montagewijze","Montage","montage"}, montage);
        addIfExists(cols, insertCols, bindArgs, new String[]{"Pictogram","pictogram"}, pictogram);
        addIfExists(cols, insertCols, bindArgs, new String[]{"Accutype","accutype","Accu type","accu_type","Batterijtype","batterijtype"}, accuType);
        addIfExists(cols, insertCols, bindArgs, new String[]{"Artikelnr","ArtikelNr","Artikelnr.","artikelnummer","Artikelnummer","artikelnr","artikelnr."}, artikelNr);
        addIfExists(cols, insertCols, bindArgs, new String[]{"Accu leeftijd","AccuLeeftijd","accu_leeftijd","Accu (leeftijd)","accu leeftijd"}, accuLeeftijd);
        addIfExists(cols, insertCols, bindArgs, new String[]{"ATS","ats","Autotest","autotest"}, ats);
        addIfExists(cols, insertCols, bindArgs, new String[]{"Duurtest","duurtest","Duurtest (min)","duurtest_min"}, duurtest);
        addIfExists(cols, insertCols, bindArgs, new String[]{"Opmerking","opmerking","Notitie","notitie","Notes","notes"}, opmerking);

        try {
            if (insertCols.isEmpty()) {
                // Volledig lege rij: nullColumnHack-equivalent
                String any = pickAnyColumn(cols);
                String sql = "INSERT INTO " + quoteId(TABLE_INSPECTIES) +
                        "(" + quoteId(any) + ") VALUES (NULL)";
                db.execSQL(sql);
            } else {
                StringBuilder sb = new StringBuilder("INSERT INTO ");
                sb.append(quoteId(TABLE_INSPECTIES)).append("(");
                for (int i = 0; i < insertCols.size(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append(quoteId(insertCols.get(i)));
                }
                sb.append(") VALUES (");
                for (int i = 0; i < insertCols.size(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append('?');
                }
                sb.append(')');

                db.execSQL(sb.toString(), bindArgs.toArray());
            }

            Toast.makeText(this, "Armatuur toegevoegd", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Opslaan mislukt", e);
            Toast.makeText(this, "Opslaan mislukt", Toast.LENGTH_SHORT).show();
        }

        setResult(RESULT_OK);
        finish();
    }

    // ===== Helpers =====

    private void loadSuggestionsForLocation() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Set<String> cols = getColumns(db, TABLE_INSPECTIES);

        String locatieCol   = firstExisting(cols, "Locatie","locatie","Location","location");
        String verdiepingCol= firstExisting(cols, "Verdieping","verdieping","Floor","floor","Etage","etage");
        String ruimteCol    = firstExisting(cols, "Ruimte","ruimte","Room","room","Kamer","kamer");

        String locatieVal = nullIfEmpty(etLocatie.getText() != null ? etLocatie.getText().toString() : null);

        List<String> verdiepingen = queryDistinct(db, TABLE_INSPECTIES, verdiepingCol, locatieCol, locatieVal);
        List<String> ruimtes      = queryDistinct(db, TABLE_INSPECTIES, ruimteCol,     locatieCol, locatieVal);

        ArrayAdapter<String> verdiepingAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, verdiepingen);
        acVerdieping.setAdapter(verdiepingAdapter);

        ArrayAdapter<String> ruimteAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, ruimtes);
        acRuimte.setAdapter(ruimteAdapter);
    }

    private static void addIfExists(Set<String> cols, List<String> outCols, List<Object> outVals,
                                    String[] candidates, @Nullable String value) {
        if (value == null) return;
        String col = firstExisting(cols, candidates);
        if (col != null) {
            outCols.add(col);
            outVals.add(value);
        }
    }

    private static List<String> queryDistinct(SQLiteDatabase db, String table, String col,
                                              @Nullable String whereCol, @Nullable String whereVal) {
        List<String> out = new ArrayList<>();
        if (db == null || TextUtils.isEmpty(table) || TextUtils.isEmpty(col)) return out;

        StringBuilder sql = new StringBuilder("SELECT DISTINCT ");
        sql.append(col).append(" FROM ").append(table)
                .append(" WHERE ").append(col).append(" IS NOT NULL")
                .append(" AND TRIM(").append(col).append(") <> ''");

        List<String> args = new ArrayList<>();
        if (!TextUtils.isEmpty(whereCol) && !TextUtils.isEmpty(whereVal)) {
            sql.append(" AND ").append(whereCol).append(" = ?");
            args.add(whereVal);
        }
        sql.append(" ORDER BY ").append(col);

        Cursor c = db.rawQuery(sql.toString(), args.isEmpty() ? null : args.toArray(new String[0]));
        try {
            while (c.moveToNext()) {
                String v = c.getString(0);
                if (!TextUtils.isEmpty(v) && !out.contains(v)) out.add(v);
            }
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    private static String nullIfEmpty(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static Set<String> getColumns(SQLiteDatabase db, String table) {
        HashSet<String> out = new HashSet<>();
        Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        try {
            int idx = c.getColumnIndex("name");
            while (c.moveToNext()) out.add(c.getString(idx));
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    private static String firstExisting(Set<String> cols, String... candidates) {
        if (cols == null || candidates == null) return null;
        for (String cand : candidates)
            for (String existing : cols)
                if (existing != null && existing.equalsIgnoreCase(cand)) return existing;
        return null;
    }

    private static String pickAnyColumn(Set<String> cols) {
        if (cols == null || cols.isEmpty()) return null;
        return cols.iterator().next();
    }

    /** Quote een SQLite identifier (kolom of tabel) met dubbele aanhalingstekens. */
    private static String quoteId(String id) {
        if (id == null) throw new IllegalArgumentException("Identifier is null");
        // Escape dubbele quotes door ze te verdubbelen, conform SQLite identifier quoting
        return "\"" + id.replace("\"", "\"\"") + "\"";
    }
}