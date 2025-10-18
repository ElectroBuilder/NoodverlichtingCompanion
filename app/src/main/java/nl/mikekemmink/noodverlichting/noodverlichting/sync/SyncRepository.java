package nl.mikekemmink.noodverlichting.noodverlichting.sync;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import nl.mikekemmink.noodverlichting.noodverlichting.sync.model.DefectPushDto;
import nl.mikekemmink.noodverlichting.noodverlichting.sync.model.MarkerPushDto;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class SyncRepository {
    private static final String TAG = "SyncRepository";
    private static final String QUEUE_PREFS = "sync_queue";
    private static final String KEY_DEFECTS = "q_defects";
    private static final String KEY_MARKERS = "q_markers";

    private final Context ctx;
    private final Gson gson = new Gson();

    public SyncRepository(Context c){ this.ctx = c.getApplicationContext(); }

    // ----- QUEUE (SharedPreferences JSON) -----
    private <T> List<T> getList(String key, Type type){
        String raw = ctx.getSharedPreferences(QUEUE_PREFS, Context.MODE_PRIVATE).getString(key, "[]");
        try { return gson.fromJson(raw, type); } catch (Exception e){ return new ArrayList<>(); }
    }
    private <T> void putList(String key, List<T> list){
        String raw = gson.toJson(list);
        ctx.getSharedPreferences(QUEUE_PREFS, Context.MODE_PRIVATE).edit().putString(key, raw).apply();
    }

    public static class QueueStats { public int defects; public int markers; }
    public QueueStats getQueueStats(){
        android.content.SharedPreferences p = ctx.getSharedPreferences(QUEUE_PREFS, Context.MODE_PRIVATE);
        QueueStats s = new QueueStats();
        try {
            String d = p.getString(KEY_DEFECTS, "[]");
            String m = p.getString(KEY_MARKERS, "[]");
            s.defects = (d==null||d.length()<2)?0:new org.json.JSONArray(d).length();
            s.markers = (m==null||m.length()<2)?0:new org.json.JSONArray(m).length();
        } catch (Exception ignore){ s.defects = 0; s.markers = 0; }
        return s;
    }

    public void queueDefectPush(int inspectieId, String omschrijving, String dateYmd){
        Type t = new TypeToken<List<DefectPushDto.Item>>(){}.getType();
        List<DefectPushDto.Item> q = getList(KEY_DEFECTS, t);
        DefectPushDto.Item it = new DefectPushDto.Item();
        it.inspectie_id = inspectieId; it.omschrijving = omschrijving; it.datum = dateYmd;
        q.add(it);
        putList(KEY_DEFECTS, q);
    }

    public void queueMarkerUpsert(int inspectieId, String pdfNaam, int page1Based, double x, double y){
        Type t = new TypeToken<List<MarkerPushDto.Item>>(){}.getType();
        List<MarkerPushDto.Item> q = getList(KEY_MARKERS, t);
        MarkerPushDto.Item it = new MarkerPushDto.Item();
        it.inspectie_id = inspectieId;
        it.pdf_naam = pdfNaam;
        it.page = page1Based;
        it.x = x; it.y = y;
        it.op = "UPSERT";
        q.add(it);
        putList(KEY_MARKERS, q);
    }

    public void queueMarkerDelete(int inspectieId){
        Type t = new TypeToken<List<MarkerPushDto.Item>>(){}.getType();
        List<MarkerPushDto.Item> q = getList(KEY_MARKERS, t);
        MarkerPushDto.Item it = new MarkerPushDto.Item();
        it.inspectie_id = inspectieId;
        it.op = "DELETE";
        q.add(it);
        putList(KEY_MARKERS, q);
    }

    // ----- Stats -----
    public static class PushStats {
        public int defectsAdded, defectsSkipped;
        public int markersAdded, markersUpdated, markersSkipped;
        public int errors;
    }

    // ----- PUSH (with stats) -----
    public PushStats pushAllWithStats(SyncClient client) throws Exception {
        PushStats stats = new PushStats();

        // defects
        Type td = new TypeToken<List<DefectPushDto.Item>>(){}.getType();
        List<DefectPushDto.Item> defects = getList(KEY_DEFECTS, td);
        if(!defects.isEmpty()){
            DefectPushDto env = new DefectPushDto(); env.defects = defects;
            try {
                String resp = client.pushDefects(gson.toJson(env));
                JSONObject obj = new JSONObject(resp);
                stats.defectsAdded = obj.optInt("added", 0);
                stats.defectsSkipped = obj.optInt("skipped", 0);
                putList(KEY_DEFECTS, new ArrayList<>()); // leeg na succes
            } catch (Exception ex) { stats.errors++; throw ex; }
        }

        // markers -> plain array to /push_markers_json
        Type tm = new TypeToken<List<MarkerPushDto.Item>>(){}.getType();
        List<MarkerPushDto.Item> markers = getList(KEY_MARKERS, tm);
        if(!markers.isEmpty()){
            List<MarkerPushDto.Item> merged = mergeMarkerOps(markers);
            JSONArray arr = new JSONArray();
            List<MarkerPushDto.Item> keepInQueue = new ArrayList<>(); // keep DELETE until server supports it

            for(MarkerPushDto.Item it: merged){
                if ("DELETE".equals(it.op)) { keepInQueue.add(it); continue; }
                JSONObject o = new JSONObject();
                o.put("inspectie_id", it.inspectie_id);
                if(it.pdf_naam != null) o.put("pdf_naam", it.pdf_naam);
                if(it.page != null && it.page > 0) o.put("page", it.page);
                o.put("x", it.x);
                o.put("y", it.y);
                arr.put(o);
            }

            if (arr.length() > 0) {
                try {
                    String resp = client.pushMarkersJson(arr.toString());
                    JSONObject obj = new JSONObject(resp);
                    stats.markersAdded = obj.optInt("added", 0);
                    stats.markersUpdated = obj.optInt("updated", 0);
                    stats.markersSkipped = obj.optInt("skipped", 0);
                } catch (Exception ex) { stats.errors++; throw ex; }
            }

            // queue: keep DELETE, clear others
            putList(KEY_MARKERS, keepInQueue);
        }

        return stats;
    }

    // Old API (kept for compatibility if needed)
    public void pushAll(SyncClient client) throws Exception { pushAllWithStats(client); }

    // merge by inspectie_id (latest op wins, UPSERT overwrites, DELETE wins)
    private List<MarkerPushDto.Item> mergeMarkerOps(List<MarkerPushDto.Item> in){
        Map<Integer, MarkerPushDto.Item> m = new LinkedHashMap<>();
        for(MarkerPushDto.Item it: in){
            MarkerPushDto.Item prev = m.get(it.inspectie_id);
            if(prev == null){ m.put(it.inspectie_id, it); continue; }
            if("DELETE".equals(it.op)){ m.put(it.inspectie_id, it); continue; }
            m.put(it.inspectie_id, it); // UPSERT overwrites previous
        }
        return new ArrayList<>(m.values());
    }

    // ----- PULL -> apply -----
    public void applyPullJson(String raw) throws Exception {
        JSONObject obj = new JSONObject(raw);
        if(!obj.has("changes")) return;
        JSONArray arr = obj.getJSONArray("changes");
        String newestTs = SyncConfig.lastSync(ctx);
        for(int i=0;i<arr.length();i++){
            JSONObject row = arr.getJSONObject(i);
            String table = row.optString("table","");
            String op = row.optString("op","");
            String ts = row.optString("ts", newestTs);
            String payload = row.optString("payload", null);
            if(ts.compareTo(newestTs) > 0) newestTs = ts;
            if("gebreken".equalsIgnoreCase(table)){
                applyDefectChange(op, payload);
            } else if ("armatuur_posities".equalsIgnoreCase(table)){
                applyMarkerChange(op, payload);
            }
        }
        SyncConfig.setLastSync(ctx, newestTs);
    }

    private void applyDefectChange(String op, String payload) {
        if(payload == null) return;
        try{
            JSONObject p = new JSONObject(payload);
            int inspectieId = p.optInt("inspectie_id",-1);
            String oms = p.optString("omschrijving","");
            String datum = p.optString("datum", "");
            String foto = p.optString("foto_pad", null);
            SQLiteDatabase fdb = nl.mikekemmink.noodverlichting.noodverlichting.data.DBField
                    .getInstance(ctx).getWritableDatabase();
            if("DELETE".equals(op)){
                fdb.delete("gebreken",
                        "inspectie_id=? AND COALESCE(omschrijving,'')=? AND COALESCE(datum,'')=?",
                        new String[]{String.valueOf(inspectieId), oms, datum});
            } else {
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put("inspectie_id", inspectieId);
                cv.put("omschrijving", oms);
                if(datum!=null && !datum.isEmpty()) cv.put("datum", datum);
                if(foto!=null && !foto.isEmpty()) cv.put("foto_pad", foto);
                fdb.insert("gebreken", null, cv);
            }
        }catch(Exception ignore){}
    }

    private File markersDir(){
        File dir = new File(ctx.getFilesDir(),"markers");
        if(!dir.exists()) dir.mkdirs();
        return dir;
    }
    private File markersFile(){ return new File(markersDir(),"markers.json"); }

    private void backupMarkers(){
        try{
            File f = markersFile();
            if(!f.exists()) return;
            String ts = new SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(new java.util.Date());
            File bakDir = new File(markersDir(),"backup");
            if(!bakDir.exists()) bakDir.mkdirs();
            File bak = new File(bakDir, "markers_"+ts+".json");
            try(java.io.FileInputStream in = new java.io.FileInputStream(f);
                java.io.FileOutputStream out = new java.io.FileOutputStream(bak)){
                byte[] buf = new byte[8192]; int n; while((n=in.read(buf))>0) out.write(buf,0,n);
            }
        }catch(Exception ignore){}
    }

    private JSONArray readJsonArray(File f){
        try{
            if(!f.exists() || f.length()==0) return new JSONArray();
            try(InputStream in = new FileInputStream(f)){
                String txt = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                if(txt.startsWith("\uFEFF")) txt = txt.substring(1);
                txt = txt.trim();
                if(txt.isEmpty()) return new JSONArray();
                return txt.startsWith("[") ? new JSONArray(txt) : new JSONArray().put(new JSONObject(txt));
            }
        }catch(Exception e){
            return new JSONArray();
        }
    }

    private void writeJsonArray(File f, JSONArray arr){
        try(FileOutputStream out = new FileOutputStream(f,false)){
            byte[] bytes = arr.toString(2).getBytes(StandardCharsets.UTF_8);
            out.write(bytes);
            out.flush();
        }catch(Exception ignore){}
    }

    private void applyMarkerChange(String op, String payload) {
        // **BELANGRIJK**: we negeren DELETE-events voor markers om verlies te voorkomen
        // en we maken eerst een back-up van markers.json voordat we wijzigen.
        try {
            backupMarkers();
            JSONArray arr = readJsonArray(markersFile());

            if("DELETE".equals(op)){
                // Voor nu: NIETS doen bij DELETE. We ondersteunen marker-verwijdering later met expliciete user-intent.
                Log.w(TAG, "applyMarkerChange: DELETE voor markers genegeerd");
                return;
            }
            if(payload == null || payload.isEmpty()) return;

            JSONObject p = new JSONObject(payload);
            int inspectieId = p.optInt("inspectie_id",-1);
            String pdf = p.optString("pdf_naam","");
            int page1 = p.optInt("page",1);
            double x = p.optDouble("x", Double.NaN);
            double y = p.optDouble("y", Double.NaN);
            boolean done=false;
            for(int i=0;i<arr.length();i++){
                JSONObject it = arr.getJSONObject(i);
                if(it.optInt("inspectie_id",-1) == inspectieId){
                    it.put("pdf_naam", pdf);
                    it.put("page", page1);
                    it.put("x", x); it.put("y", y);
                    done=true; break;
                }
            }
            if(!done){
                JSONObject it = new JSONObject();
                it.put("inspectie_id", inspectieId);
                it.put("pdf_naam", pdf);
                it.put("page", page1);
                it.put("x", x); it.put("y", y);
                arr.put(it);
            }
            writeJsonArray(markersFile(), arr);
        } catch (Exception e){
            Log.e(TAG, "applyMarkerChange", e);
        }
    }
}
