package nl.mikekemmink.noodverlichting.noodverlichting.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;

import java.util.HashSet;
import java.util.Set;

/**
 * Scant de lokale field-DB (gebreken) en zet nieuwe records in de push-wachtrij.
 * - leest huidige queue uit SharedPreferences ("sync_queue" / "q_defects")
 * - vergelijkt met 'seen' set in SharedPreferences ("sync_seen" / "seen_defects")
 * - queue't alleen nieuwe combinaties (inspectie_id|omschrijving|datum)
 */
public class DefectQueueScanner {
    private static final String SEEN_PREFS = "sync_seen";
    private static final String KEY_SEEN_DEFECTS = "seen_defects"; // JSON array van keys
    private static final String QUEUE_PREFS = "sync_queue";
    private static final String KEY_Q_DEFECTS = "q_defects";

    private static String k(int insp, String oms, String dt){
        if(oms==null) oms=""; if(dt==null) dt="";
        return insp + "|" + oms + "|" + dt;
    }

    public static int scanAndQueueNew(Context ctx){
        // Load seen
        SharedPreferences seen = ctx.getSharedPreferences(SEEN_PREFS, Context.MODE_PRIVATE);
        Set<String> seenSet = new HashSet<>();
        try {
            String raw = seen.getString(KEY_SEEN_DEFECTS, "[]");
            JSONArray arr = new JSONArray(raw);
            for(int i=0;i<arr.length();i++) seenSet.add(arr.getString(i));
        } catch (Exception ignore) {}

        // Load queued keys (huidige queue in prefs)
        Set<String> queuedKeys = new HashSet<>();
        try {
            String rawQ = ctx.getSharedPreferences(QUEUE_PREFS, Context.MODE_PRIVATE).getString(KEY_Q_DEFECTS, "[]");
            JSONArray arrQ = new JSONArray(rawQ);
            for(int i=0;i<arrQ.length();i++){
                try{
                    org.json.JSONObject o = arrQ.getJSONObject(i);
                    int insp = o.optInt("inspectie_id", -1);
                    String oms = o.optString("omschrijving", "");
                    String dt  = o.optString("datum", "");
                    queuedKeys.add(k(insp, oms, dt));
                }catch(Exception ignore){}
            }
        } catch (Exception ignore) {}

        // Query lokale DB (field)
        SQLiteDatabase fdb = nl.mikekemmink.noodverlichting.noodverlichting.data.DBField
                .getInstance(ctx).getReadableDatabase();

        int queued = 0;
        Cursor c = null;
        try {
            c = fdb.rawQuery("SELECT inspectie_id, COALESCE(omschrijving,''), COALESCE(datum,'') FROM gebreken", null);
            SyncRepository repo = new SyncRepository(ctx);
            while(c.moveToNext()){
                int insp = c.getInt(0);
                String oms = c.getString(1);
                String dt  = c.getString(2);
                String key = k(insp, oms, dt);
                if(seenSet.contains(key)) continue;     // al eerder verwerkt
                if(queuedKeys.contains(key)) continue;   // staat al in queue
                // queue nieuw
                repo.queueDefectPush(insp, oms, dt);
                queued++;
            }
        } finally { if(c!=null) c.close(); }
        return queued;
    }

    /** Markeer alle huidig-gequeue'de gebreken als 'seen' zodat de scanner ze niet opnieuw queue't. */
    public static void markCurrentQueueAsSeen(Context ctx){
        try {
            SharedPreferences prefsQ = ctx.getSharedPreferences(QUEUE_PREFS, Context.MODE_PRIVATE);
            String rawQ = prefsQ.getString(KEY_Q_DEFECTS, "[]");
            JSONArray arrQ = new JSONArray(rawQ);

            SharedPreferences seen = ctx.getSharedPreferences(SEEN_PREFS, Context.MODE_PRIVATE);
            String rawS = seen.getString(KEY_SEEN_DEFECTS, "[]");
            JSONArray arrS = new JSONArray(rawS);
            Set<String> sset = new HashSet<>();
            for(int i=0;i<arrS.length();i++) sset.add(arrS.getString(i));

            boolean changed = false;
            for(int i=0;i<arrQ.length();i++){
                try{
                    org.json.JSONObject o = arrQ.getJSONObject(i);
                    String key = k(o.optInt("inspectie_id", -1), o.optString("omschrijving",""), o.optString("datum",""));
                    if(!sset.contains(key)) { arrS.put(key); sset.add(key); changed = true; }
                }catch(Exception ignore){}
            }
            if(changed){
                seen.edit().putString(KEY_SEEN_DEFECTS, arrS.toString()).apply();
            }
        } catch (Exception ignore){}
    }
}
