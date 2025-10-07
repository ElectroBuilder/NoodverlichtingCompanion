
package nl.mikekemmink.noodverlichting.nen3140;

import android.content.Context;
import androidx.annotation.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Opslag voor NEN3140 (los van de noodverlichting-DB): JSON-bestanden in
 * files/nen3140/
 *  - locations.json
 *  - location_<id>.json (boards + measurements + defects)
 */
public class NenStorage {
    // ====== VELDEN ======
    private final Context ctx;
    private final File root;
    private final File locationsFile;
    private final File photosDir;

    public NenStorage(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        root = new File(this.ctx.getFilesDir(), "nen3140");
        if (!root.exists()) root.mkdirs();
        locationsFile = new File(root, "locations.json");
        photosDir = new File(root, "photos");
        if (!photosDir.exists()) photosDir.mkdirs();
    }

    public File getPhotosDir() { return photosDir; }

    // ====== I/O helpers ======
    private static String readAll(File f) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }
    private static void writeAll(File f, String s) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(f, false))) {
            bw.write(s);
        } catch (Exception ignore) {}
    }
    private File locationFile(String locationId) { return new File(root, "location_" + locationId + ".json"); }
    private JSONObject ensureLocationFile(String locationId) {
        File f = locationFile(locationId);
        if (!f.exists()) {
            JSONObject o = new JSONObject();
            try { o.put("boards", new JSONArray()); } catch (Exception ignore) {}
            writeAll(f, o.toString());
            return o;
        } else {
            try { return new JSONObject(readAll(f)); } catch (Exception e) { return new JSONObject(); }
        }
    }

    // ====== LOCATIONS ======
    public List<NenLocation> loadLocations() {
        try {
            if (!locationsFile.exists()) return new ArrayList<>();
            JSONArray arr = new JSONArray(readAll(locationsFile));
            List<NenLocation> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new NenLocation(o.getString("id"), o.getString("name")));
            }
            return out;
        } catch (Exception e) { return new ArrayList<>(); }
    }
    public void addLocation(String name) {
        List<NenLocation> list = loadLocations();
        NenLocation loc = new NenLocation(name);
        list.add(loc);
        saveLocations(list);
        ensureLocationFile(loc.getId());
    }
    public void deleteLocation(String id) {
        List<NenLocation> list = loadLocations();
        list.removeIf(l -> l.getId().equals(id));
        saveLocations(list);
        File f = locationFile(id);
        if (f.exists()) f.delete();
    }
    private void saveLocations(List<NenLocation> list) {
        JSONArray arr = new JSONArray();
        for (NenLocation l : list) {
            JSONObject o = new JSONObject();
            try {
                o.put("id", l.getId());
                o.put("name", l.getName());
                arr.put(o);
            } catch (Exception ignore) {}
        }
        writeAll(locationsFile, arr.toString());
    }

    // ====== BOARDS ======
    public List<NenBoard> loadBoards(String locationId) {
        try {
            JSONObject o = ensureLocationFile(locationId);
            JSONArray arr = o.optJSONArray("boards");
            List<NenBoard> list = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject b = arr.getJSONObject(i);
                    NenBoard board = new NenBoard(b.getString("name"));
                    // herstel id via reflectie
                    try {
                        java.lang.reflect.Field fid = NenBoard.class.getDeclaredField("id");
                        fid.setAccessible(true);
                        fid.set(board, b.getString("id"));
                    } catch (Exception ignore) {}

                    // Metingen inlezen (laatste blijft enige in app)
                    JSONArray marr = b.optJSONArray("measurements");
                    if (marr != null) {
                        for (int j = 0; j < marr.length(); j++) {
                            JSONObject mj = marr.getJSONObject(j);
                            String mid = mj.optString("id", null);
                            if (mid == null || mid.isEmpty()) { mid = "ts-" + mj.optLong("timestamp", System.currentTimeMillis()); }
                            NenMeasurement m = new NenMeasurement(
                                    mid,
                                    mj.optDouble("L1", 0), mj.optDouble("L2", 0),
                                    mj.optDouble("L3", 0), mj.optDouble("N", 0),
                                    mj.optDouble("PE", 0), mj.optLong("timestamp", 0)
                            );
                            board.getMeasurements().add(m);
                        }
                    }

                    // Gebreken inlezen: mocht er nog een enkelvoudig object "defect" bestaan, converteer naar array
                    JSONArray darr = b.optJSONArray("defects");
                    if (darr == null) {
                        JSONObject single = b.optJSONObject("defect");
                        if (single != null) {
                            darr = new JSONArray();
                            darr.put(single);
                            try { b.put("defects", darr); b.remove("defect"); writeAll(locationFile(locationId), o.toString()); } catch (Exception ignore) {}
                        }
                    }
                    if (darr != null) {
                        for (int j = 0; j < darr.length(); j++) {
                            JSONObject dj = darr.optJSONObject(j);
                            if (dj == null) continue;
                            String id = dj.optString("id", java.util.UUID.randomUUID().toString());
                            String text = dj.optString("text", null);
                            String photo = dj.optString("photo", null);
                            long ts = dj.optLong("timestamp", System.currentTimeMillis());
                            board.getDefects().add(new NenDefect(id, text, photo, ts));
                        }
                    }

                    list.add(board);
                }
            }
            return list;
        } catch (Exception e) { return new ArrayList<>(); }
    }

    public void addBoard(String locationId, String name) {
        JSONObject root = ensureLocationFile(locationId);
        JSONArray arr = root.optJSONArray("boards");
        if (arr == null) { arr = new JSONArray(); try { root.put("boards", arr); } catch (Exception ignore) {} }
        JSONObject b = new JSONObject();
        try {
            b.put("id", java.util.UUID.randomUUID().toString());
            b.put("name", name);
            b.put("measurements", new JSONArray());
            b.put("defects", new JSONArray());
            arr.put(b);
            writeAll(locationFile(locationId), root.toString());
        } catch (Exception ignore) {}
    }

    public void deleteBoard(String locationId, String boardId) {
        JSONObject root = ensureLocationFile(locationId);
        JSONArray arr = root.optJSONArray("boards");
        if (arr == null) return;
        JSONArray out = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject b = arr.optJSONObject(i);
            if (b != null && !boardId.equals(b.optString("id"))) out.put(b);
        }
        try { root.put("boards", out); } catch (Exception ignore) {}
        writeAll(locationFile(locationId), root.toString());
    }

    // ====== MEASUREMENTS ======
    public void setSingleMeasurement(String locationId, String boardId, NenMeasurement m) {
        if (m.id == null || m.id.isEmpty()) m.id = java.util.UUID.randomUUID().toString();
        JSONObject root = ensureLocationFile(locationId);
        JSONArray arr = root.optJSONArray("boards");
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject b = arr.optJSONObject(i);
            if (b != null && boardId.equals(b.optString("id"))) {
                JSONArray marr = new JSONArray();
                JSONObject mj = new JSONObject();
                try {
                    mj.put("id", m.id);
                    mj.put("L1", m.L1); mj.put("L2", m.L2); mj.put("L3", m.L3);
                    mj.put("N", m.N);   mj.put("PE", m.PE);
                    mj.put("timestamp", m.timestamp > 0 ? m.timestamp : System.currentTimeMillis());
                    marr.put(mj);
                    b.put("measurements", marr);
                    writeAll(locationFile(locationId), root.toString());
                } catch (Exception ignore) {}
                return;
            }
        }
    }
    public void addMeasurement(String locationId, String boardId, NenMeasurement m) { setSingleMeasurement(locationId, boardId, m); }

    public void patchBoardMeasurement(String locationId, String boardId,
                                      @Nullable Double L1,
                                      @Nullable Double L2,
                                      @Nullable Double L3,
                                      @Nullable Double N,
                                      @Nullable Double PE,
                                      @Nullable Long timestamp) {
        JSONObject root = ensureLocationFile(locationId);
        JSONArray arr = root.optJSONArray("boards");
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject b = arr.optJSONObject(i);
            if (b == null || !boardId.equals(b.optString("id"))) continue;
            JSONArray marr = b.optJSONArray("measurements");
            JSONObject mj = (marr != null && marr.length() > 0) ? marr.optJSONObject(0) : null;
            if (mj == null) mj = new JSONObject();
            try {
                if (L1 != null) mj.put("L1", L1); else if (!mj.has("L1")) mj.put("L1", 0);
                if (L2 != null) mj.put("L2", L2); else if (!mj.has("L2")) mj.put("L2", 0);
                if (L3 != null) mj.put("L3", L3); else if (!mj.has("L3")) mj.put("L3", 0);
                if (N  != null) mj.put("N",  N ); else if (!mj.has("N"))  mj.put("N", 0);
                if (PE != null) mj.put("PE", PE); else if (!mj.has("PE")) mj.put("PE", 0);
                if (!mj.has("id")) mj.put("id", java.util.UUID.randomUUID().toString());
                if (timestamp != null) mj.put("timestamp", timestamp);
                else if (L1 != null || L2 != null || L3 != null || N != null || PE != null) mj.put("timestamp", System.currentTimeMillis());
                else if (!mj.has("timestamp")) mj.put("timestamp", System.currentTimeMillis());
                JSONArray newArr = new JSONArray(); newArr.put(mj); b.put("measurements", newArr);
                writeAll(locationFile(locationId), root.toString());
            } catch (Exception ignore) {}
            return;
        }
    }

    // ====== DEFECTS ======
    public List<NenDefect> loadDefects(String locationId, String boardId) {
        List<NenDefect> out = new ArrayList<>();
        JSONObject root = ensureLocationFile(locationId);
        JSONArray arr = root.optJSONArray("boards");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject b = arr.optJSONObject(i);
            if (b != null && boardId.equals(b.optString("id"))) {
                JSONArray darr = b.optJSONArray("defects");
                if (darr != null) {
                    for (int j = 0; j < darr.length(); j++) {
                        JSONObject dj = darr.optJSONObject(j);
                        if (dj == null) continue;
                        out.add(new NenDefect(
                                dj.optString("id", java.util.UUID.randomUUID().toString()),
                                dj.optString("text", null),
                                dj.optString("photo", null),
                                dj.optLong("timestamp", System.currentTimeMillis())
                        ));
                    }
                }
                break;
            }
        }
        return out;
    }

    public NenDefect addDefect(String locationId, String boardId, @Nullable String text) {
        JSONObject root = ensureLocationFile(locationId);
        JSONArray arr = root.optJSONArray("boards");
        if (arr == null) return null;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject b = arr.optJSONObject(i);
            if (b != null && boardId.equals(b.optString("id"))) {
                JSONArray darr = b.optJSONArray("defects");
                if (darr == null) { darr = new JSONArray(); try { b.put("defects", darr); } catch (Exception ignore) {} }
                JSONObject dj = new JSONObject();
                String id = java.util.UUID.randomUUID().toString();
                long ts = System.currentTimeMillis();
                try {
                    dj.put("id", id);
                    if (text != null && !text.trim().isEmpty()) dj.put("text", text);
                    dj.put("timestamp", ts);
                    darr.put(dj);
                    writeAll(locationFile(locationId), root.toString());
                    return new NenDefect(id, text, null, ts);
                } catch (Exception ignore) { return null; }
            }
        }
        return null;
    }

    public void updateDefectText(String locationId, String boardId, String defectId, @Nullable String text) {
        JSONObject root = ensureLocationFile(locationId);
        JSONArray arr = root.optJSONArray("boards");
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject b = arr.optJSONObject(i);
            if (b != null && boardId.equals(b.optString("id"))) {
                JSONArray darr = b.optJSONArray("defects");
                if (darr == null) return;
                for (int j = 0; j < darr.length(); j++) {
                    JSONObject dj = darr.optJSONObject(j);
                    if (dj != null && defectId.equals(dj.optString("id"))) {
                        try {
                            if (text == null || text.trim().isEmpty()) dj.remove("text"); else dj.put("text", text);
                            dj.put("timestamp", System.currentTimeMillis());
                            writeAll(locationFile(locationId), root.toString());
                        } catch (Exception ignore) {}
                        return;
                    }
                }
            }
        }
    }

    public void setDefectPhoto(String locationId, String boardId, String defectId, @Nullable String fileName) {
        JSONObject root = ensureLocationFile(locationId);
        JSONArray arr = root.optJSONArray("boards");
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject b = arr.optJSONObject(i);
            if (b != null && boardId.equals(b.optString("id"))) {
                JSONArray darr = b.optJSONArray("defects");
                if (darr == null) return;
                for (int j = 0; j < darr.length(); j++) {
                    JSONObject dj = darr.optJSONObject(j);
                    if (dj != null && defectId.equals(dj.optString("id"))) {
                        try {
                            if (fileName == null || fileName.trim().isEmpty()) dj.remove("photo"); else dj.put("photo", fileName);
                            dj.put("timestamp", System.currentTimeMillis());
                            writeAll(locationFile(locationId), root.toString());
                        } catch (Exception ignore) {}
                        return;
                    }
                }
            }
        }
    }

    public void deleteDefect(String locationId, String boardId, String defectId, boolean deletePhotoFile) {
        JSONObject root = ensureLocationFile(locationId);
        JSONArray arr = root.optJSONArray("boards");
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject b = arr.optJSONObject(i);
            if (b != null && boardId.equals(b.optString("id"))) {
                JSONArray darr = b.optJSONArray("defects");
                if (darr == null) return;
                JSONArray out = new JSONArray();
                for (int j = 0; j < darr.length(); j++) {
                    JSONObject dj = darr.optJSONObject(j);
                    if (dj != null && defectId.equals(dj.optString("id"))) {
                        if (deletePhotoFile) {
                            String fn = dj.optString("photo", null);
                            if (fn != null) {
                                File f = new File(photosDir, fn);
                                if (f.exists()) f.delete();
                            }
                        }
                        continue; // skip deze -> effectively delete
                    }
                    out.put(dj);
                }
                try { b.put("defects", out); } catch (Exception ignore) {}
                writeAll(locationFile(locationId), root.toString());
                return;
            }
        }
    }

    // ====== OPSCHONING (metingen) ======
    public void compressToSingleLatest(String locationId) {
        JSONObject root = ensureLocationFile(locationId);
        JSONArray arr = root.optJSONArray("boards");
        if (arr == null) return;
        boolean changed = false;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject b = arr.optJSONObject(i);
            if (b == null) continue;
            JSONArray marr = b.optJSONArray("measurements");
            if (marr == null || marr.length() <= 1) continue;
            int latestIdx = 0; long maxTs = Long.MIN_VALUE;
            for (int j = 0; j < marr.length(); j++) {
                JSONObject mj = marr.optJSONObject(j);
                long ts = (mj != null) ? mj.optLong("timestamp", Long.MIN_VALUE) : Long.MIN_VALUE;
                if (ts > maxTs) { maxTs = ts; latestIdx = j; }
            }
            JSONObject latest = marr.optJSONObject(latestIdx);
            JSONArray newArr = new JSONArray();
            if (latest != null) newArr.put(latest);
            try { b.put("measurements", newArr); changed = true; } catch (Exception ignore) {}
        }
        if (changed) writeAll(locationFile(locationId), root.toString());
    }
    /**
     * Compat: haal een meting op op basis van measurementId.
     * - Bestaat er geen meting met dit id? Dan geven we de (enige/laatste) meting terug als fallback.
     * - Bestaat er helemaal nog geen meting? Dan retourneert de methode null.
     */
    public @Nullable NenMeasurement getMeasurement(String locationId, String boardId, @Nullable String measurementId) {
        JSONObject root = ensureLocationFile(locationId);
        JSONArray arr = root.optJSONArray("boards");
        if (arr == null) return null;

        for (int i = 0; i < arr.length(); i++) {
            JSONObject b = arr.optJSONObject(i);
            if (b != null && boardId.equals(b.optString("id"))) {
                JSONArray marr = b.optJSONArray("measurements");
                if (marr == null || marr.length() == 0) return null;

                // 1) Probeer exact id-match (voor oudere code die nog een id doorgeeft)
                if (measurementId != null && !measurementId.isEmpty()) {
                    for (int j = 0; j < marr.length(); j++) {
                        JSONObject mj = marr.optJSONObject(j);
                        if (mj != null && measurementId.equals(mj.optString("id"))) {
                            return new NenMeasurement(
                                    mj.optString("id"),
                                    mj.optDouble("L1", 0), mj.optDouble("L2", 0),
                                    mj.optDouble("L3", 0), mj.optDouble("N", 0),
                                    mj.optDouble("PE", 0), mj.optLong("timestamp", 0)
                            );
                        }
                    }
                }

                // 2) Fallback: returneer de enige of laatste meting
                //    (we houden in deze app toch max. 1 meting per board aan)
                JSONObject latest = marr.optJSONObject(0);
                long maxTs = (latest != null) ? latest.optLong("timestamp", Long.MIN_VALUE) : Long.MIN_VALUE;
                for (int j = 1; j < marr.length(); j++) {
                    JSONObject mj = marr.optJSONObject(j);
                    if (mj == null) continue;
                    long ts = mj.optLong("timestamp", Long.MIN_VALUE);
                    if (ts > maxTs) { latest = mj; maxTs = ts; }
                }
                if (latest == null) return null;
                return new NenMeasurement(
                        latest.optString("id"),
                        latest.optDouble("L1", 0), latest.optDouble("L2", 0),
                        latest.optDouble("L3", 0), latest.optDouble("N", 0),
                        latest.optDouble("PE", 0), latest.optLong("timestamp", 0)
                );
            }
        }
        return null;
    }
}
