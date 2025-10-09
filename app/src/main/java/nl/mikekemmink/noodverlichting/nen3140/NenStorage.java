package nl.mikekemmink.noodverlichting.nen3140;

import android.content.Context;
import androidx.annotation.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

public class NenStorage {
    private final Context context;
    private final File baseDir;

    public NenStorage(Context ctx){
        this.context = ctx;
        this.baseDir = new File(ctx.getFilesDir(), "nen3140");
        if (!baseDir.exists()) baseDir.mkdirs();
    }

    // ==== Locations ====
    private File locationsFile(){ return new File(baseDir, "locations.json"); }

    public List<NenLocation> loadLocations(){
        ArrayList<NenLocation> list = new ArrayList<>();
        try {
            JSONArray arr = readJsonArray(locationsFile());
            for (int i=0;i<arr.length();i++) list.add(NenLocation.fromJson(arr.getJSONObject(i)));
        } catch (Exception ignore) {}
        return list;
    }

    public void addLocation(String name){
        try {
            File f = locationsFile();
            JSONArray arr = readJsonArray(f);
            String id = UUID.randomUUID().toString();
            NenLocation loc = new NenLocation(id, name);
            arr.put(loc.toJson());
            writeJsonArray(f, arr);
        } catch (Exception ignore) {}
    }

    public void deleteLocation(String locationId){
        try {
            // 1) Remove location from list
            File lf = locationsFile();
            JSONArray larr = readJsonArray(lf);
            JSONArray lout = new JSONArray();
            for (int i=0;i<larr.length();i++){
                JSONObject o = larr.getJSONObject(i);
                if (!locationId.equals(o.optString("id"))) lout.put(o);
            }
            writeJsonArray(lf, lout);

            // 2) Delete all boards (and their files) for this location
            File bf = boardsFile(locationId);
            JSONArray barr = readJsonArray(bf);
            for (int i=0;i<barr.length();i++){
                JSONObject bo = barr.getJSONObject(i);
                String boardId = bo.optString("id", null);
                if (boardId != null) deleteBoard(locationId, boardId);
            }
            if (bf.exists()) bf.delete();
        } catch (Exception ignore) {}
    }

    // ==== Boards ====
    private File boardsFile(String locationId){
        return new File(baseDir, "boards_"+locationId+".json");
    }

    public List<NenBoard> loadBoards(String locationId){
        ArrayList<NenBoard> list = new ArrayList<>();
        try {
            JSONArray arr = readJsonArray(boardsFile(locationId));
            for (int i=0;i<arr.length();i++) list.add(NenBoard.fromJson(arr.getJSONObject(i)));
        } catch (Exception ignore) {}
        return list;
    }

    public void addBoard(String locationId, String name){
        try {
            File f = boardsFile(locationId);
            JSONArray arr = readJsonArray(f);
            String id = UUID.randomUUID().toString();
            NenBoard b = new NenBoard(id, name);
            arr.put(b.toJson());
            writeJsonArray(f, arr);
        } catch (Exception ignore) {}
    }

    public void deleteBoard(String locationId, String boardId){
        try {
            File f = boardsFile(locationId);
            JSONArray arr = readJsonArray(f);
            JSONArray out = new JSONArray();
            for (int i=0;i<arr.length();i++){
                JSONObject o = arr.getJSONObject(i);
                if (!boardId.equals(o.optString("id"))) out.put(o);
            }
            writeJsonArray(f, out);

            // measurements file cleanup
            File mf = measurementsFile(locationId, boardId);
            if (mf.exists()) mf.delete();

            // defects cleanup: verwijder gekoppelde foto's en defects-bestand
            File df = defectsFile(locationId, boardId);
            if (df.exists()) {
                try {
                    JSONArray darr = readJsonArray(df);
                    File photosDir = getPhotosDir();
                    for (int i = 0; i < darr.length(); i++) {
                        JSONObject o = darr.getJSONObject(i);
                        String photo = o.optString("photo", null);
                        if (photo != null) {
                            File pf = new File(photosDir, photo);
                            if (pf.exists()) pf.delete();
                        }
                    }
                } catch (Exception ignore) {}
                df.delete();
            }
        } catch (Exception ignore) {}
    }

    @Nullable
    public NenBoard getBoard(String locationId, String boardId){
        try{
            JSONArray arr = readJsonArray(boardsFile(locationId));
            for (int i=0;i<arr.length();i++){
                JSONObject o = arr.getJSONObject(i);
                if (boardId.equals(o.optString("id"))) return NenBoard.fromJson(o);
            }
        }catch(Exception ignore){}
        return null;
    }

    public void updateBoardPhotos(String locationId, String boardId, @Nullable String photoBoardPath, @Nullable String photoInfoPath){
        try{
            File f = boardsFile(locationId);
            JSONArray arr = readJsonArray(f);
            JSONArray out = new JSONArray();
            for (int i=0;i<arr.length();i++){
                JSONObject o = arr.getJSONObject(i);
                if (boardId.equals(o.optString("id"))){
                    NenBoard b = NenBoard.fromJson(o);
                    if (photoBoardPath != null) b.setPhotoBoardPath(photoBoardPath);
                    if (photoInfoPath != null) b.setPhotoInfoPath(photoInfoPath);
                    o = b.toJson();
                }
                out.put(o);
            }
            writeJsonArray(f, out);
        }catch(Exception ignore){}
    }

    public boolean hasBoardPhotos(String locationId, String boardId){
        NenBoard b = getBoard(locationId, boardId);
        if (b == null) return false;
        return (b.getPhotoBoardPath() != null && !b.getPhotoBoardPath().isEmpty())
                || (b.getPhotoInfoPath() != null && !b.getPhotoInfoPath().isEmpty());
    }

    // ==== Measurements ====
    private File measurementsFile(String locationId, String boardId){
        return new File(baseDir, "measure_"+locationId+"_"+boardId+".json");
    }

    public void addMeasurement(String locationId, String boardId, NenMeasurement m){
        try {
            if (m.id == null) m.id = UUID.randomUUID().toString();
            JSONArray arr = readJsonArray(measurementsFile(locationId, boardId));
            arr.put(m.toJson());
            writeJsonArray(measurementsFile(locationId, boardId), arr);
        } catch (Exception ignore) {}
    }

    @Nullable
    public NenMeasurement getMeasurement(String locationId, String boardId, String measurementId){
        try {
            JSONArray arr = readJsonArray(measurementsFile(locationId, boardId));
            for (int i=0;i<arr.length();i++){
                JSONObject o = arr.getJSONObject(i);
                if (measurementId.equals(o.optString("id"))) return NenMeasurement.fromJson(o);
            }
        } catch (Exception ignore) {}
        return null;
    }

    @Nullable
    public NenMeasurement getLastMeasurement(String locationId, String boardId){
        try {
            JSONArray arr = readJsonArray(measurementsFile(locationId, boardId));
            long maxTs = -1; JSONObject last = null;
            for (int i=0;i<arr.length();i++){
                JSONObject o = arr.getJSONObject(i);
                long ts = o.optLong("timestamp", 0);
                if (ts > maxTs){ maxTs = ts; last = o; }
            }
            if (last != null) return NenMeasurement.fromJson(last);
        } catch (Exception ignore) {}
        return null;
    }

    public boolean hasSpdValues(String locationId, String boardId){
        NenMeasurement m = getLastMeasurement(locationId, boardId);
        if (m == null) return false;
        return (m.spdL1 != null) || (m.spdL2 != null) || (m.spdL3 != null) || (m.spdN != null);
    }

    // ==== Defects ====
    private File defectsFile(String locationId, String boardId) {
        return new File(baseDir, "defects_" + locationId + "_" + boardId + ".json");
    }

    public File getPhotosDir() {
        File d = new File(baseDir, "photos");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public List<NenDefect> loadDefects(String locationId, String boardId) {
        ArrayList<NenDefect> list = new ArrayList<>();
        try {
            JSONArray arr = readJsonArray(defectsFile(locationId, boardId));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String id = o.optString("id", null);
                String text = o.has("text") ? o.optString("text", null) : null;
                String photo = o.has("photo") ? o.optString("photo", null) : null;
                long ts = o.optLong("timestamp", System.currentTimeMillis());
                list.add(new NenDefect(id, text, photo, ts));
            }
        } catch (Exception ignore) {}
        return list;
    }

    public NenDefect addDefect(String locationId, String boardId, @Nullable String text) {
        try {
            String id = UUID.randomUUID().toString();
            long ts = System.currentTimeMillis();

            JSONArray arr = readJsonArray(defectsFile(locationId, boardId));
            JSONObject o = new JSONObject();
            o.put("id", id);
            if (text != null) o.put("text", text);
            o.put("timestamp", ts);
            arr.put(o);
            writeJsonArray(defectsFile(locationId, boardId), arr);
            return new NenDefect(id, text, null, ts);
        } catch (Exception ignore) {}
        return null;
    }

    public void updateDefectText(String locationId, String boardId, String defectId, @Nullable String text) {
        try {
            File f = defectsFile(locationId, boardId);
            JSONArray arr = readJsonArray(f);
            JSONArray out = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (defectId.equals(o.optString("id"))) {
                    if (text == null) o.remove("text"); else o.put("text", text);
                }
                out.put(o);
            }
            writeJsonArray(f, out);
        } catch (Exception ignore) {}
    }

    public void setDefectPhoto(String locationId, String boardId, String defectId, @Nullable String fileName) {
        try {
            File f = defectsFile(locationId, boardId);
            JSONArray arr = readJsonArray(f);
            JSONArray out = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (defectId.equals(o.optString("id"))) {
                    if (fileName == null) o.remove("photo"); else o.put("photo", fileName);
                }
                out.put(o);
            }
            writeJsonArray(f, out);
        } catch (Exception ignore) {}
    }

    public void deleteDefect(String locationId, String boardId, String defectId, boolean alsoDeletePhoto) {
        try {
            File f = defectsFile(locationId, boardId);
            JSONArray arr = readJsonArray(f);
            JSONArray out = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (defectId.equals(o.optString("id"))) {
                    if (alsoDeletePhoto) {
                        String photo = o.optString("photo", null);
                        if (photo != null) {
                            File pf = new File(getPhotosDir(), photo);
                            if (pf.exists()) pf.delete();
                        }
                    }
                    // skip toevoegen -> effectively delete
                } else {
                    out.put(o);
                }
            }
            writeJsonArray(f, out);
        } catch (Exception ignore) {}
    }

    // ==== IO helpers ====
    private static JSONArray readJsonArray(File f) throws Exception {
        if (!f.exists()) return new JSONArray();
        try (BufferedReader br = new BufferedReader(new FileReader(f))){
            StringBuilder sb = new StringBuilder();
            String line; while ((line = br.readLine()) != null) sb.append(line);
            String s = sb.toString().trim();
            if (s.isEmpty()) return new JSONArray();
            return new JSONArray(s);
        }
    }

    private static void writeJsonArray(File f, JSONArray arr) throws Exception {
        if (!f.getParentFile().exists()) f.getParentFile().mkdirs();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(f, false))){
            bw.write(arr.toString());
        }
    }
    public void updateLocationName(String locationId, String newName) {
        try {
            File f = locationsFile();
            JSONArray arr = readJsonArray(f);
            JSONArray out = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (locationId.equals(o.optString("id"))) {
                    o.put("name", newName);
                }
                out.put(o);
            }
            writeJsonArray(f, out);
        } catch (Exception ignore) { }
    }
    // In NenStorage.java (bij de Boards-sectie plaatsen)
    public void updateBoardName(String locationId, String boardId, String newName) {
        try {
            File f = boardsFile(locationId);
            JSONArray arr = readJsonArray(f);
            JSONArray out = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (boardId.equals(o.optString("id"))) {
                    o.put("name", newName);
                }
                out.put(o);
            }
            writeJsonArray(f, out);
        } catch (Exception ignore) { }
    }
}