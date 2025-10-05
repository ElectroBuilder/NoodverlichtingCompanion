package nl.mikekemmink.noodverlichting.columns;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ColumnConfigManager {

    private static final String PREFS = "col_prefs";
    private static final String KEY_ORDER = "columns_order";

    // Standaard set (en volgorde) – pas aan naar jouw voorkeur
    public static List<ColumnConfig> getDefault() {
        List<ColumnConfig> list = new ArrayList<>();
        list.add(new ColumnConfig("inspectieid", "Inspectie-ID", true));
        list.add(new ColumnConfig("nr",            "Nr.",            false));
        list.add(new ColumnConfig("code",          "Code",           true));
        list.add(new ColumnConfig("soort",         "Soort",          true));
        list.add(new ColumnConfig("verdieping",    "Verdieping",     false));
        list.add(new ColumnConfig("op_tekening",   "Op tekening",    false));
        list.add(new ColumnConfig("type",          "Type",           false));
        list.add(new ColumnConfig("merk",          "Merk",           false));
        list.add(new ColumnConfig("montagewijze",  "Montagewijze",   false));
        list.add(new ColumnConfig("pictogram",     "Pictogram",      false));
        list.add(new ColumnConfig("accutype",      "Accutype",       false));
        list.add(new ColumnConfig("artikelnr",     "Artikelnr",      false));
        list.add(new ColumnConfig("accu_leeftijd", "Accu leeftijd",  false));
        list.add(new ColumnConfig("ats",           "ATS",            false));
        list.add(new ColumnConfig("duurtest",      "Duurtest",       false));
        list.add(new ColumnConfig("opmerking",     "Opmerking",      false));
        return list;
    }

    public static List<ColumnConfig> load(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = sp.getString(KEY_ORDER, null);
        if (json == null) return getDefault();

        try {
            JSONArray arr = new JSONArray(json);
            List<ColumnConfig> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new ColumnConfig(
                        o.optString("alias"),
                        o.optString("label"),
                        o.optBoolean("visible", true)
                ));
            }
            return out;
        } catch (Exception e) {
            return getDefault();
        }
    }

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
}