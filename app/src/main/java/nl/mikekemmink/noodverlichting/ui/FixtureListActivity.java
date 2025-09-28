
package nl.mikekemmink.noodverlichting.ui;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nl.mikekemmink.noodverlichting.BaseActivity;
import nl.mikekemmink.noodverlichting.IToolbarActions;
import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.columns.ColumnConfig;
import nl.mikekemmink.noodverlichting.columns.ColumnConfigManager;
import nl.mikekemmink.noodverlichting.columns.ColumnSettingsActivity;
import nl.mikekemmink.noodverlichting.data.DBHelper;

public class FixtureListActivity extends BaseActivity implements IToolbarActions {

    private static final String TAG = "FixtureListActivity";
    private static final String PREFS = "app_prefs";
    private static final String KEY_LAST_LOCATIE = "last_locatie";

    private DBHelper dbHelper;
    private Cursor cursor;
    private ListView list;
    private FixtureRowAdapter adapter;
    private ScrollSyncManager syncManager = new ScrollSyncManager();
    private View headerView;
    private boolean showDefects = false; // toolbar-toggle
    private DefectProvider defectProvider;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_with_toolbar);

        MaterialToolbar tb = findViewById(R.id.toolbar);
        attachToolbar(tb);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        getLayoutInflater().inflate(R.layout.activity_fixture_list, findViewById(R.id.content_container), true);

        String locatie = getIntent() != null ? getIntent().getStringExtra(LocationListActivity.EXTRA_LOCATIE) : null;
        if (locatie == null || locatie.trim().isEmpty()) {
            locatie = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_LAST_LOCATIE, null);
        }
        if (locatie == null || locatie.trim().isEmpty()) {
            Intent back = new Intent(this, LocationListActivity.class);
            back.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(back);
            finish();
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_LAST_LOCATIE, locatie).apply();
        if (getSupportActionBar() != null) getSupportActionBar().setTitle(locatie);

        dbHelper = new DBHelper(this);
        defectProvider = new DefectProvider(dbHelper);
        list = findViewById(R.id.listArmaturen);
        list.setLongClickable(true);
        list.setItemsCanFocus(false);

        load(locatie);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setToolbarActions(this);
        reload();
    }

    @Override
    protected void onPause() {
        super.onPause();
        setToolbarActions(null);
    }

    private void load(String locatie) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Set<String> cols = getColumns(db, "inspecties");
        Log.d(TAG, "Kolommen in 'inspecties': " + cols);

        String locCol        = firstExisting(cols, "Locatie","locatie","Location","location");
        String nrCol         = firstExisting(cols, "Nr.","Nr","nr.","nr","Nummer","nummer","ArmatuurNr","armatuur_nr","armatuurnr");
        String codeCol       = firstExisting(cols, "Code","code","ArmatuurCode","armatuurcode","arm_code");
        String soortCol      = firstExisting(cols, "Soort","soort","Type","type");
        String verdiepingCol = firstExisting(cols, "Verdieping","verdieping","Floor","floor","Etage","etage");
        String opTekCol      = firstExisting(cols, "Op tekening","Op_Tekening","OpTekening","op_tekening","optekening");
        String typeCol       = firstExisting(cols, "Type","type");
        String merkCol       = firstExisting(cols, "Merk","merk","Armatuur merk","armatuur_merk","ArmatuurMerk");
        String montageCol    = firstExisting(cols, "Montagewijze","montagewijze","Montage","montage");
        String pictogramCol  = firstExisting(cols, "Pictogram","pictogram");
        String accuTypeCol   = firstExisting(cols, "Accutype","accutype","Accu type","accu_type","Batterijtype","batterijtype");
        String artikelNrCol  = firstExisting(cols, "Artikelnr","ArtikelNr","Artikelnr.","artikelnummer","Artikelnummer","artikelnr","artikelnr.");
        String accuLeeftCol  = firstExisting(cols, "Accu leeftijd","AccuLeeftijd","accu_leeftijd","Accu (leeftijd)","accu leeftijd");
        String atsCol        = firstExisting(cols, "ATS","ats","Autotest","autotest");
        String duurtestCol   = firstExisting(cols, "Duurtest","duurtest","Duurtest (min)","duurtest_min");
        String opmCol        = firstExisting(cols, "Opmerking","opmerking","Notitie","notitie","Notes","notes");

        if (locCol == null) {
            Toast.makeText(this, "Kolom 'Locatie' niet gevonden", Toast.LENGTH_LONG).show();
            return;
        }

        String sql =
                "SELECT rowid AS _id, " +
                        sel(nrCol, "nr") + ", " +
                        sel(codeCol, "code") + ", " +
                        sel(soortCol, "soort") + ", " +
                        sel(verdiepingCol, "verdieping") + ", " +
                        sel(opTekCol, "op_tekening") + ", " +
                        sel(typeCol, "type") + ", " +
                        sel(merkCol, "merk") + ", " +
                        sel(montageCol, "montagewijze") + ", " +
                        sel(pictogramCol, "pictogram") + ", " +
                        sel(accuTypeCol, "accutype") + ", " +
                        sel(artikelNrCol, "artikelnr") + ", " +
                        sel(accuLeeftCol, "accu_leeftijd") + ", " +
                        sel(atsCol, "ats") + ", " +
                        sel(duurtestCol, "duurtest") + ", " +
                        sel(opmCol, "opmerking") + " " +
                        "FROM inspecties WHERE " + q(locCol) + " = ? ORDER BY " + (nrCol != null ? q(nrCol) : (codeCol != null ? q(codeCol) : "rowid"));

        // Adapter tijdelijk loskoppelen
        if (list.getAdapter() != null) list.setAdapter(null);

        // Kolombreedtes + header verversen (inclusief optionele 'Gebreken'-kolom)
        Map<String, Integer> colWidthsPx = computeColumnWidthsPx();
        List<ColumnConfig> cfg = ColumnConfigManager.load(this);
        boolean anyVisible = false;
        for (ColumnConfig c0 : cfg) { if (c0.visible) { anyVisible = true; break; } }
        if (!anyVisible) {
            cfg = ColumnConfigManager.getDefault();
            ColumnConfigManager.save(this, cfg);
            Toast.makeText(this, "Kolommen hersteld naar standaard", Toast.LENGTH_SHORT).show();
        }
        buildHeader(cfg, colWidthsPx, showDefects);

        // Cursor wisselen
        if (cursor != null && !cursor.isClosed()) cursor.close();
        cursor = db.rawQuery(sql, new String[]{ locatie });

        if (cursor != null && cursor.getCount() > 0) {
            adapter = new FixtureRowAdapter(this, cursor, cfg, syncManager,
                    (inspectieId, fixtureCode, position) -> showRowMenu(inspectieId, fixtureCode),
                    colWidthsPx, defectProvider, showDefects);
            list.setAdapter(adapter);
        } else {
            adapter = null;
            list.setAdapter(null);
            Toast.makeText(this, "Geen armaturen voor: " + locatie, Toast.LENGTH_SHORT).show();
        }

        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_LAST_LOCATIE, locatie).apply();
    }

    private void showRowMenu(final int inspectieId, final String fixtureCode) {
        CharSequence[] items = new CharSequence[]{"Gebreken bekijken","Gebrek toevoegen","Inspectie-ID kopiëren"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(fixtureCode != null && !fixtureCode.isEmpty() ? fixtureCode : ("Inspectie #" + inspectieId))
                .setItems(items, (dialog, which) -> {
                    if (which == 0) startDefectList(inspectieId, fixtureCode);
                    else if (which == 1) startAddDefect(inspectieId, fixtureCode);
                    else {
                        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("inspectie_id", String.valueOf(inspectieId)));
                        Toast.makeText(this, "Inspectie-ID gekopieerd", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void startAddDefect(int inspectieId, String fixtureCode) {
        Intent i = new Intent(this, AddDefectActivity.class);
        i.putExtra(AddDefectActivity.EXTRA_INSPECTIE_ID, inspectieId);
        i.putExtra(AddDefectActivity.EXTRA_TITEL, (fixtureCode != null && !fixtureCode.isEmpty()) ? ("Armatuur: " + fixtureCode) : ("Armatuur #" + inspectieId));
        String locatie = getIntent() != null ? getIntent().getStringExtra(LocationListActivity.EXTRA_LOCATIE) : null;
        if (locatie == null) locatie = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_LAST_LOCATIE, null);
        if (locatie != null) i.putExtra(LocationListActivity.EXTRA_LOCATIE, locatie);
        startActivity(i);
    }

    private void startDefectList(int inspectieId, String fixtureCode) {
        Intent i = new Intent(this, DefectListActivity.class);
        i.putExtra(DefectListActivity.EXTRA_INSPECTIE_ID, inspectieId);
        i.putExtra(DefectListActivity.EXTRA_TITEL, (fixtureCode != null ? ("Gebreken - " + fixtureCode) : ("Gebreken #" + inspectieId)));
        String locatie = getIntent() != null ? getIntent().getStringExtra(LocationListActivity.EXTRA_LOCATIE) : null;
        if (locatie == null) locatie = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_LAST_LOCATIE, null);
        if (locatie != null) i.putExtra(LocationListActivity.EXTRA_LOCATIE, locatie);
        startActivity(i);
    }

    private void reload() {
        String locatie = getIntent() != null ? getIntent().getStringExtra(LocationListActivity.EXTRA_LOCATIE) : null;
        if (locatie == null || locatie.trim().isEmpty())
            locatie = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_LAST_LOCATIE, null);
        if (locatie != null) {
            if (getSupportActionBar() != null) getSupportActionBar().setTitle(locatie);
            load(locatie);
        }
    }

    // ---------- IToolbarActions ----------
    @Override public boolean isDefectsShown() { return showDefects; }
    @Override public void onToggleDefects(boolean show) {
        showDefects = show;
        // Header opnieuw opbouwen met/zonder extra kolom
        Map<String, Integer> colWidthsPx = computeColumnWidthsPx();
        List<ColumnConfig> cfg = ColumnConfigManager.load(this);
        buildHeader(cfg, colWidthsPx, showDefects);
        if (adapter != null) {
            adapter.setShowDefects(showDefects);
        }
    }
    @Override public void onColumnsClicked() { startActivity(new Intent(this, ColumnSettingsActivity.class)); }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cursor != null && !cursor.isClosed()) cursor.close();
    }

    // ---------- Helpers ----------
    private static java.util.Set<String> getColumns(SQLiteDatabase db, String table) {
        java.util.HashSet<String> out = new java.util.HashSet<>();
        android.database.Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        try { int idx = c.getColumnIndex("name"); while (c.moveToNext()) out.add(c.getString(idx)); }
        finally { if (c != null) c.close(); }
        return out;
    }
    private static String firstExisting(java.util.Set<String> cols, String... candidates) {
        for (String cand : candidates) for (String existing : cols)
            if (existing != null && existing.equalsIgnoreCase(cand)) return existing; return null;
    }
    private static String q(String ident) { return "`" + ident + "`"; }
    private static String sel(String sourceCol, String alias) { return (sourceCol != null) ? (q(sourceCol) + " AS " + alias) : ("NULL AS " + alias); }

    private int widthFor(String alias) {
        if ("nr".equals(alias)) return 72; if ("code".equals(alias)) return 128; if ("soort".equals(alias)) return 96;
        if ("verdieping".equals(alias)) return 110; if ("op_tekening".equals(alias)) return 130; if ("type".equals(alias)) return 96;
        if ("merk".equals(alias)) return 128; if ("montagewijze".equals(alias)) return 160; if ("pictogram".equals(alias)) return 120;
        if ("accutype".equals(alias)) return 120; if ("artikelnr".equals(alias)) return 120; if ("accu_leeftijd".equals(alias)) return 140;
        if ("ats".equals(alias)) return 80; if ("duurtest".equals(alias)) return 110; if ("opmerking".equals(alias)) return 220; 
        if ("gebreken".equals(alias)) return 200; // nieuw: breedte voor gebreken-kolom
        return 120;
    }
    private int dp(int v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }
    private int px(int vDp) { return dp(vDp); }

    private Map<String, Integer> computeColumnWidthsPx() {
        HashMap<String, Integer> m = new HashMap<>();
        String[] aliases = new String[]{
                "nr","code","soort","verdieping","op_tekening","type","merk","montagewijze",
                "pictogram","accutype","artikelnr","accu_leeftijd","ats","duurtest","opmerking","gebreken"
        };
        for (String a : aliases) m.put(a, px(widthFor(a)));
        return m;
    }

    private void buildHeader(List<ColumnConfig> cfg, Map<String, Integer> colWidthsPx, boolean withDefects) {
        if (headerView == null) {
            headerView = getLayoutInflater().inflate(R.layout.header_fixture_columns, list, false);
            list.addHeaderView(headerView, null, false);
        }
        SyncHorizontalScrollView hsv = headerView.findViewById(R.id.hscrollHeader);
        if (hsv != null) hsv.setSyncer(syncManager);

        LinearLayout container = headerView.findViewById(R.id.headerContainer);
        if (container != null) {
            container.removeAllViews();
            for (ColumnConfig cc : cfg) {
                if (!cc.visible) continue;
                TextView tv = new TextView(this);
                tv.setText(cc.label);
                tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                tv.setTextColor(0xff222222);
                tv.setSingleLine(true);
                tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                tv.setPadding(dp(0), dp(0), dp(12), dp(0));
                Integer w = colWidthsPx != null ? colWidthsPx.get(cc.alias) : null;
                int widthPx = (w != null ? w : px(120));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(widthPx, LinearLayout.LayoutParams.WRAP_CONTENT);
                tv.setLayoutParams(lp);
                tv.setMinWidth(widthPx);
                tv.setMaxWidth(widthPx);
                container.addView(tv);
            }
            if (withDefects) {
                TextView tv = new TextView(this);
                tv.setText("Gebreken");
                tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                tv.setTextColor(0xff222222);
                tv.setSingleLine(true);
                tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                tv.setPadding(dp(0), dp(0), dp(12), dp(0));
                Integer w = colWidthsPx != null ? colWidthsPx.get("gebreken") : null;
                int widthPx = (w != null ? w : px(200));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(widthPx, LinearLayout.LayoutParams.WRAP_CONTENT);
                tv.setLayoutParams(lp);
                tv.setMinWidth(widthPx);
                tv.setMaxWidth(widthPx);
                container.addView(tv);
            }
        }
        if (hsv != null) {
            final int x = syncManager.getCurrentX();
            hsv.post(() -> hsv.syncTo(x));
        }
    }

    // ------- DefectProvider: haalt per inspectieId een korte samenvatting op -------
    static class DefectProvider {
        private final DBHelper helper;
        private final LruCache<Integer, String> cache = new LruCache<>(256);
        DefectProvider(DBHelper helper) { this.helper = helper; }

        String summaryFor(int inspectieId) {
            String v = cache.get(inspectieId);
            if (v != null) return v;
            SQLiteDatabase db = null; Cursor d = null;
            try {
                db = helper.getReadableDatabase();
                // Probeer met omschrijving + count
                try {
                    d = db.rawQuery("SELECT COUNT(*) AS cnt, GROUP_CONCAT(omschrijving, ' | ') AS oms FROM gebreken WHERE inspectie_id = ?",
                            new String[]{ String.valueOf(inspectieId) });
                    if (d.moveToFirst()) {
                        int cnt = d.getInt(d.getColumnIndexOrThrow("cnt"));
                        String oms = null;
                        int idx = d.getColumnIndex("oms"); if (idx >= 0) oms = d.getString(idx);
                        v = (cnt <= 0) ? "" : (cnt + "× " + (oms != null ? oms : "gebrek"));
                    }
                } catch (Exception e) {
                    // Fallback: alleen count als de kolomnaam of tabel verschilt
                    if (d != null) { d.close(); d = null; }
                    d = db.rawQuery("SELECT COUNT(*) AS cnt FROM gebreken WHERE inspectie_id = ?",
                            new String[]{ String.valueOf(inspectieId) });
                    if (d.moveToFirst()) {
                        int cnt = d.getInt(d.getColumnIndexOrThrow("cnt"));
                        v = (cnt <= 0) ? "" : (cnt + "× gebrek(en)");
                    }
                }
            } catch (Exception ignore) {
                v = ""; // laat kolom leeg als iets misgaat
            } finally {
                if (d != null) d.close();
                // db wordt beheerd door helper
            }
            if (v == null) v = "";
            cache.put(inspectieId, v);
            return v;
        }
    }
}
