package nl.mikekemmink.noodverlichting.stroom;

import nl.mikekemmink.noodverlichting.stroom.StroomWaardeEntry;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import org.json.JSONArray;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import android.util.Log;
public class StroomRepo {
    private static final String FILE_NAME = "stroomwaardes.json"; // interne app-opslag
    private static final String TAG = "StroomRepo";
    private static File getFile(Context ctx) {
        return new File(ctx.getFilesDir(), FILE_NAME);
    }

    private static JSONObject readObject(Context ctx) {
        File f = getFile(ctx);
        if (!f.exists()) return new JSONObject();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            String json = sb.toString().trim();
            if (json.isEmpty()) return new JSONObject();

            Object any = new JSONTokener(json).nextValue();
            if (any instanceof JSONObject) {
                return (JSONObject) any;
            }
            if (any instanceof JSONArray) {
                // MIGRATIE: array -> object per kast
                JSONObject migrated = migrateArrayToObject((JSONArray) any);
                // Schrijf meteen terug in nieuw formaat
                writeObject(ctx, migrated);
                return migrated;
            }
            // Onbekend type
            return new JSONObject();
        } catch (Exception e) {
            e.printStackTrace();
            return new JSONObject();
        }
    }

    private static JSONObject migrateArrayToObject(JSONArray arr) {
        JSONObject obj = new JSONObject();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject it = arr.optJSONObject(i);
            if (it == null) continue;

            // Haal kastnaam op; als leeg -> fallback unieke sleutel
            String kast = it.optString("kastNaam", "").trim();
            if (kast.isEmpty()) kast = "kast_" + (i + 1);

            // Laatste schrijft wint (per kast 1 item)
            try {
                obj.put(kast, it);
            } catch (Exception ignore) {}
        }
        return obj;
    }


    private static void writeObject(Context ctx, JSONObject obj) {
        File f = getFile(ctx);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(f, false))) {
            bw.write(obj.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Retourneer ALLE actuele waarden, maximaal 1 per kast (per-kast opslag).
     */
    public static List<StroomWaardeEntry> getAll(Context ctx) {
        JSONObject obj = readObject(ctx);
        List<StroomWaardeEntry> list = new ArrayList<StroomWaardeEntry>();
        for (Iterator<String> it = obj.keys(); it.hasNext(); ) {
            String key = it.next();
            JSONObject val = obj.optJSONObject(key);
            if (val == null) continue;
            try {
                StroomWaardeEntry e = StroomWaardeEntry.fromJson(val);
                e.kastNaam = key;
                list.add(e);
            } catch (Exception ignore) {}
        }
        Log.d(TAG, "GET_ALL -> " + list.size() + " metingen");
        Collections.sort(list, new Comparator<StroomWaardeEntry>() {
            @Override public int compare(StroomWaardeEntry a, StroomWaardeEntry b) {
                return a.kastNaam.compareToIgnoreCase(b.kastNaam);
            }
        });
        return list;
    }

    /**
     * Zet of vervang de waarden voor een kast (laatste meting per kast).
     */
    public static void put(Context ctx, StroomWaardeEntry entry) {
        JSONObject obj = readObject(ctx);
        try {
            obj.put(entry.kastNaam, entry.toJson());
        } catch (JSONException ignore) {}

        // LOG: voor schrijven
        File f = getFile(ctx);
        Log.d(TAG, "PUT kast=" + entry.kastNaam + " -> " + f.getAbsolutePath());

        writeObject(ctx, obj);

        // LOG: na schrijven
        Log.d(TAG, "WROTE size=" + f.length());
    }


    public static StroomWaardeEntry getByKast(Context ctx, String kastNaam) {
        JSONObject obj = readObject(ctx);
        JSONObject val = obj.optJSONObject(kastNaam);
        if (val == null) return null;
        try {
            StroomWaardeEntry e = StroomWaardeEntry.fromJson(val);
            e.kastNaam = kastNaam;
            return e;
        } catch (JSONException e) {
            return null;
        }
    }

    public static void deleteByKast(Context ctx, String kastNaam) {
        JSONObject obj = readObject(ctx);
        obj.remove(kastNaam);
        writeObject(ctx, obj);
    }

    public static void clear(Context ctx) {
        writeObject(ctx, new JSONObject());
    }
}
