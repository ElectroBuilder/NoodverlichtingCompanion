package nl.mikekemmink.noodverlichting.noodverlichting.columns;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ColumnConfigManager {
    private static final String PREFS = "col_prefs";
    private static final String KEY_ORDER = "columns_order";

    /** Defaults (incl. nieuwe kolom “Ruimte”). */
    public static List<ColumnConfig> getDefault() {
        List<ColumnConfig> list = new ArrayList<>();
        list.add(new ColumnConfig("inspectieid",  "Inspectie-ID",  true));
        list.add(new ColumnConfig("nr",           "Nr.",           false));
        list.add(new ColumnConfig("code",         "Code",          true));
        list.add(new ColumnConfig("soort",        "Soort",         true));
        list.add(new ColumnConfig("verdieping",   "Verdieping",    false));
        list.add(new ColumnConfig("ruimte",       "Ruimte",        true));   // ⬅️ nieuw
        list.add(new ColumnConfig("op_tekening",  "Op tekening",   false));
        list.add(new ColumnConfig("type",         "Type",          false));
        list.add(new ColumnConfig("merk",         "Merk",          false));
        list.add(new ColumnConfig("montagewijze", "Montagewijze",  false));
        list.add(new ColumnConfig("pictogram",    "Pictogram",     false));
        list.add(new ColumnConfig("accutype",     "Accutype",      false));
        list.add(new ColumnConfig("artikelnr",    "Artikelnr",     false));
        list.add(new ColumnConfig("accu_leeftijd","Accu leeftijd", false));
        list.add(new ColumnConfig("ats",          "ATS",           false));
        list.add(new ColumnConfig("duurtest",     "Duurtest",      false));
        list.add(new ColumnConfig("opmerking",    "Opmerking",     false));
        return list;
    }

    /** Laad prefs; voeg ontbrekende defaults (zoals “Ruimte”) automatisch toe. */
    public static List<ColumnConfig> load(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = sp.getString(KEY_ORDER, null);

        List<ColumnConfig> out;
        if (json == null) {
            out = getDefault();
        } else {
            try {
                JSONArray arr = new JSONArray(json);
                out = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    out.add(new ColumnConfig(
                            o.optString("alias"),
                            o.optString("label"),
                            o.optBoolean("visible", true)
                    ));
                }
            } catch (Exception e) {
                out = getDefault();
            }
            out = mergeWithDefaults(out); // ⬅️ voegt nieuwe kolommen in bestaande prefs in
        }
        return out;
    }

    /** Bewaar prefs als JSON. */
    public static void save(Context ctx, List<ColumnConfig> list) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray arr = new JSONArray();
        try {
            for (ColumnConfig c : list) {
                JSONObject o = new JSONObject();
                o.put("alias", c.alias);
                o.put("label", c.label);
                o.put("visible", c.visible);
                arr.put(o);
            }
        } catch (Exception ignored) { }
        android.util.Log.d("Columns", "SAVE_JSON -> " + arr.toString());
        sp.edit().putString(KEY_ORDER, arr.toString()).apply();
    }

    // ---- helpers ----

    /** Merge: behoud user-volgorde; voeg ontbrekende defaults (b.v. “ruimte”) toe. */
    private static List<ColumnConfig> mergeWithDefaults(List<ColumnConfig> current) {
        List<ColumnConfig> merged = new ArrayList<>(current);
        Set<String> have = new HashSet<>();
        for (ColumnConfig c : current) have.add(c.alias == null ? "" : c.alias);

        for (ColumnConfig def : getDefault()) {
            String alias = def.alias == null ? "" : def.alias;
            if (!have.contains(alias)) {
                // plaats “Ruimte” direct na “Verdieping” indien aanwezig
                if ("ruimte".equals(alias)) {
                    int idx = -1;
                    for (int i = 0; i < merged.size(); i++) {
                        if ("verdieping".equalsIgnoreCase(merged.get(i).alias)) { idx = i; break; }
                    }
                    if (idx >= 0) merged.add(idx + 1, new ColumnConfig(def.alias, def.label, def.visible));
                    else          merged.add(new ColumnConfig(def.alias, def.label, def.visible));
                } else {
                    merged.add(new ColumnConfig(def.alias, def.label, def.visible));
                }
            } else {
                // ontbrekend label aanvullen met default
                for (int i = 0; i < merged.size(); i++) {
                    ColumnConfig c = merged.get(i);
                    if (alias.equalsIgnoreCase(c.alias)) {
                        if (c.label == null || c.label.trim().isEmpty()) c.label = def.label;
                        break;
                    }
                }
            }
        }
        return merged;
    }
}