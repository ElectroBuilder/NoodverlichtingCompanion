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
 * - locations.json
 * - location_<id>.json (boards + measurements)
 */
public class NenStorage {

    // ====== VELDEN (ontbraken bij jou) ======
    private final File root;
    private final File locationsFile;

    public NenStorage(Context ctx) {
        root = new File(ctx.getFilesDir(), "nen3140");
        if (!root.exists()) root.mkdirs();
        locationsFile = new File(root, "locations.json");
    }

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

    private File locationFile(String locationId) {
        return new File(root, "location_" + locationId + ".json");
    }

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
        } catch (Exception e) {
            return new ArrayList<>();
        }
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

                    JSONArray marr = b.optJSONArray("measurements");
                    if (marr != null) {
                        for (int j = 0; j < marr.length(); j++) {
                            JSONObject mj = marr.getJSONObject(j);
                            String mid = mj.optString("id", null);
                            if (mid == null || mid.isEmpty()) {
                                // backwards compat voor oude data
                                mid = "ts-" + mj.optLong("timestamp", System.currentTimeMillis());
                            }
                            NenMeasurement m = new NenMeasurement(
                                    mid,
                                    mj.optDouble("L1", 0), mj.optDouble("L2", 0),
                                    mj.optDouble("L3", 0), mj.optDouble("N", 0),
                                    mj.optDouble("PE", 0), mj.optLong("timestamp", 0)
                            );
                            board.getMeasurements().add(m);
                        }
                    }
                    list.add(board);
                }
            }
            return list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
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
    public void addMeasurement(String locationId, String boardId, NenMeasurement m) {
        if (m.id == null || m.id.isEmpty()) m.id = java.util.UUID.randomUUID().toString();
        JSONObject root = ensureLocationFile(locationId);
        JSONArray arr = root.optJSONArray("boards");
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject b = arr.optJSONObject(i);
            if (b != null && boardId.equals(b.optString("id"))) {
                JSONArray marr = b.optJSONArray("measurements");
                if (marr == null) { marr = new JSONArray(); try { b.put("measurements", marr); } catch (Exception ignore) {} }
                JSONObject mj = new JSONObject();
                try {
                    mj.put("id", m.id);
                    mj.put("L1", m.L1); mj.put("L2", m.L2); mj.put("L3", m.L3);
                    mj.put("N", m.N); mj.put("PE", m.PE); mj.put("timestamp", m.timestamp);
                    marr.put(mj);
                    writeAll(locationFile(locationId), root.toString());
                } catch (Exception ignore) {}
                return;
            }
        }
    }

    public @Nullable NenMeasurement getMeasurement(String locationId, String boardId, String measurementId) {
        JSONObject root = ensureLocationFile(locationId);
        JSONArray arr = root.optJSONArray("boards");
        if (arr == null) return null;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject b = arr.optJSONObject(i);
            if (b != null && boardId.equals(b.optString("id"))) {
                JSONArray marr = b.optJSONArray("measurements");
                if (marr == null) return null;
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
        }
        return null;
    }

    public void updateMeasurement(String locationId, String boardId, String measurementId, NenMeasurement updated) {
        JSONObject root = ensureLocationFile(locationId);
        JSONArray arr = root.optJSONArray("boards");
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject b = arr.optJSONObject(i);
            if (b != null && boardId.equals(b.optString("id"))) {
                JSONArray marr = b.optJSONArray("measurements");
                if (marr == null) return;
                for (int j = 0; j < marr.length(); j++) {
                    JSONObject mj = marr.optJSONObject(j);
                    if (mj != null && measurementId.equals(mj.optString("id"))) {
                        try {
                            mj.put("L1", updated.L1);
                            mj.put("L2", updated.L2);
                            mj.put("L3", updated.L3);
                            mj.put("N",  updated.N);
                            mj.put("PE", updated.PE);
                            mj.put("timestamp", updated.timestamp);
                            writeAll(locationFile(locationId), root.toString());
                        } catch (Exception ignore) {}
                        return;
                    }
                }
            }
        }
    }

    public void deleteMeasurement(String locationId, String boardId, String measurementId) {
        JSONObject root = ensureLocationFile(locationId);
        JSONArray arr = root.optJSONArray("boards");
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject b = arr.optJSONObject(i);
            if (b != null && boardId.equals(b.optString("id"))) {
                JSONArray marr = b.optJSONArray("measurements");
                if (marr == null) return;
                JSONArray out = new JSONArray();
                for (int j = 0; j < marr.length(); j++) {
                    JSONObject mj = marr.optJSONObject(j);
                    if (mj != null && !measurementId.equals(mj.optString("id"))) out.put(mj);
                }
                try { b.put("measurements", out); } catch (Exception ignore) {}
                writeAll(locationFile(locationId), root.toString());
                return;
            }
        }
    }
}