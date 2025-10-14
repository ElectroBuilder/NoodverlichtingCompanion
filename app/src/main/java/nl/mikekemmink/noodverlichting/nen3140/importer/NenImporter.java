
package nl.mikekemmink.noodverlichting.nen3140.importer;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class NenImporter {

    public interface ProgressCallback {
        void onProgress(String phase, int current, int total);
        boolean isCancelled();
    }

    public static final class ImportResult {
        public int locationsUpdated;
        public int boardsFiles;
        public int measureFiles;
        public int defectFiles;
        public int photosCopied;
    }

    public static ImportResult importFromZip(Context ctx, Uri zipUri,
                                             @Nullable ProgressCallback cb) throws Exception {
        ContentResolver cr = ctx.getContentResolver();
        File baseDir = new File(ctx.getFilesDir(), "nen3140");
        if (!baseDir.exists()) baseDir.mkdirs();
        File photosDir = new File(baseDir, "photos");
        if (!photosDir.exists()) photosDir.mkdirs();

        // 1) Scan-pass: tel entries voor determinate voortgang
        int totalEntries = 0;
        try (InputStream is = cr.openInputStream(zipUri);
             ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String name = e.getName();
                if (name == null) continue;
                if (name.startsWith("nen3140/")) totalEntries++;
                zis.closeEntry();
            }
        }
        if (cb != null) cb.onProgress("Scannen", 0, totalEntries);

        // 2) Verwerk-pass
        ImportResult res = new ImportResult();
        int processed = 0;
        try (InputStream is = cr.openInputStream(zipUri);
             ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (cb != null && cb.isCancelled()) throw new InterruptedException("Geannuleerd");
                String name = e.getName();
                if (name == null || !name.startsWith("nen3140/")) {
                    zis.closeEntry();
                    continue;
                }

                if (name.equals("nen3140/locations.json")) {
                    JSONArray incoming = readArrayEntry(zis);
                    File dest = new File(baseDir, "locations.json");
                    JSONArray existing = readArray(dest);
                    res.locationsUpdated += mergeLocations(existing, incoming);
                    writeArray(dest, existing);

                } else if (name.startsWith("nen3140/boards_") && name.endsWith(".json")) {
                    String fileName = name.substring("nen3140/".length());
                    File dest = new File(baseDir, fileName);
                    JSONArray incoming = readArrayEntry(zis);
                    JSONArray existing = readArray(dest);
                    mergeBoards(existing, incoming);
                    writeArray(dest, existing);
                    res.boardsFiles++;

                    /* ========== HIER INVOEGEN: varianten & split (NL/EN, per-loc, gecombineerd) ========== */
                } else if (name.startsWith("nen3140/") && name.endsWith(".json")
                        && !name.startsWith("nen3140/measure_")
                        && !name.startsWith("nen3140/defects_")
                        && !name.startsWith("nen3140/boards_")
                        && !name.equals("nen3140/locations.json")) {
                    String rel = name.substring("nen3140/".length());

                    // --- Measurements varianten ---
                    if (nameStarts(rel, "measure_", "measurement_", "metingen_")
                            || nameIs(rel, "measure.json", "measurement.json", "measurements.json", "metingen.json")) {

                        // 1) per-board: measurement_<loc>_<board>.json / metingen_<loc>_<board>.json
                        if (rel.matches("(?i)(?:measure|measurement|metingen)_.+_.+\\.json")) {
                            JSONArray incoming = readArrayEntry(zis);
                            String[] lb = extractLocBoardFromName(rel);
                            if (lb != null) {
                                String locId = lb[0], boardId = lb[1];
                                File dest = new File(baseDir, "measure_" + locId + "_" + boardId + ".json");
                                JSONArray existing = readArray(dest);
                                mergeById(existing, incoming);      // << dedup
                                writeArray(dest, existing);
                                res.measureFiles++;
                            }

                            // 2) per-loc: measurement_<loc>.json / metingen_<loc>.json  -> split naar per-board
                        } else if (rel.matches("(?i)(measure|measurement|metingen)_.+\\.json")) {
                            JSONArray incoming = readArrayEntry(zis);
                            String locFromName = extractLocOnly(rel);
                            for (int i = 0; i < incoming.length(); i++) {
                                JSONObject o = incoming.optJSONObject(i); if (o == null) continue;
                                String lid = o.optString("locationId", o.optString("locatieId",
                                        locFromName == null ? "" : locFromName));
                                String bid = o.optString("boardId", o.optString("kastId", ""));
                                if (lid.isEmpty() || bid.isEmpty()) continue;
                                File dest = new File(baseDir, "measure_" + lid + "_" + bid + ".json");
                                JSONArray existing = readArray(dest);
                                JSONArray one = new JSONArray().put(o);
                                mergeById(existing, one);           // << dedup per record
                                writeArray(dest, existing);
                                res.measureFiles++;
                            }

                            // 3) gecombineerd: measurements.json / metingen.json  -> split naar per-board
                        } else if (nameIs(rel, "measure.json", "measurement.json", "measurements.json", "metingen.json")) {
                            JSONArray incoming = readArrayEntry(zis);
                            for (int i = 0; i < incoming.length(); i++) {
                                JSONObject o = incoming.optJSONObject(i); if (o == null) continue;
                                String lid = o.optString("locationId", o.optString("locatieId", ""));
                                String bid = o.optString("boardId", o.optString("kastId", ""));
                                if (lid.isEmpty() || bid.isEmpty()) continue;
                                File dest = new File(baseDir, "measure_" + lid + "_" + bid + ".json");
                                JSONArray existing = readArray(dest);
                                JSONArray one = new JSONArray().put(o);
                                mergeById(existing, one);           // << dedup per record
                                writeArray(dest, existing);
                                res.measureFiles++;
                            }
                        }
                    }

                    // --- Defects varianten ---
                    else if (nameStarts(rel, "defects_", "gebreken_", "defecten_")
                            || nameIs(rel, "defects.json", "gebreken.json", "defecten.json")) {

                        // 1) per-board: gebreken_<loc>_<board>.json / defecten_<loc>_<board>.json
                        if (rel.matches("(?i)(defects|gebreken|defecten)_.+_.+\\.json")) {
                            JSONArray incoming = readArrayEntry(zis);
                            String[] lb = extractLocBoardFromName(rel);
                            if (lb != null) {
                                String locId = lb[0], boardId = lb[1];
                                File dest = new File(baseDir, "defects_" + locId + "_" + boardId + ".json");
                                JSONArray existing = readArray(dest);
                                mergeById(existing, incoming);      // << dedup
                                writeArray(dest, existing);
                                res.defectFiles++;
                            }

                            // 2) per-loc: gebreken_<loc>.json / defecten_<loc>.json  -> split naar per-board
                        } else if (rel.matches("(?i)(defects|gebreken|defecten)_.+\\.json")) {
                            JSONArray incoming = readArrayEntry(zis);
                            String locFromName = extractLocOnly(rel);
                            for (int i = 0; i < incoming.length(); i++) {
                                JSONObject o = incoming.optJSONObject(i); if (o == null) continue;
                                String lid = o.optString("locationId", o.optString("locatieId",
                                        locFromName == null ? "" : locFromName));
                                String bid = o.optString("boardId", o.optString("kastId", ""));
                                if (lid.isEmpty() || bid.isEmpty()) continue;
                                File dest = new File(baseDir, "defects_" + lid + "_" + bid + ".json");
                                JSONArray existing = readArray(dest);
                                JSONArray one = new JSONArray().put(o);
                                mergeById(existing, one);           // << dedup per record
                                writeArray(dest, existing);
                                res.defectFiles++;
                            }

                            // 3) gecombineerd: defects.json / gebreken.json / defecten.json -> split naar per-board
                        } else if (nameIs(rel, "defects.json", "gebreken.json", "defecten.json")) {
                            JSONArray incoming = readArrayEntry(zis);
                            for (int i = 0; i < incoming.length(); i++) {
                                JSONObject o = incoming.optJSONObject(i); if (o == null) continue;
                                String lid = o.optString("locationId", o.optString("locatieId", ""));
                                String bid = o.optString("boardId", o.optString("kastId", ""));
                                if (lid.isEmpty() || bid.isEmpty()) continue;
                                File dest = new File(baseDir, "defects_" + lid + "_" + bid + ".json");
                                JSONArray existing = readArray(dest);
                                JSONArray one = new JSONArray().put(o);
                                mergeById(existing, one);           // << dedup per record
                                writeArray(dest, existing);
                                res.defectFiles++;
                            }
                        }
                    }

                    /* ========== EINDE NIEUW BLOK ========== */

                } else if (name.startsWith("nen3140/measure_") && name.endsWith(".json")) {
                    // BESTAANDE BRANCH: alleen regel hieronder vervangen
                    String fileName = name.substring("nen3140/".length());
                    File dest = new File(baseDir, fileName);
                    JSONArray incoming = readArrayEntry(zis);
                    JSONArray existing = readArray(dest);
                    mergeById(existing, incoming);   // << vervangt appendAll(existing, incoming)
                    writeArray(dest, existing);
                    res.measureFiles++;

                } else if (name.startsWith("nen3140/defects_") && name.endsWith(".json")) {
                    // BESTAANDE BRANCH: alleen regel hieronder vervangen
                    String fileName = name.substring("nen3140/".length());
                    File dest = new File(baseDir, fileName);
                    JSONArray incoming = readArrayEntry(zis);
                    JSONArray existing = readArray(dest);
                    mergeById(existing, incoming);   // << vervangt appendAll(existing, incoming)
                    writeArray(dest, existing);
                    res.defectFiles++;

                } else if (name.startsWith("nen3140/photos/") && !e.isDirectory()) {
                    String fn = name.substring("nen3140/photos/".length());
                    if (!fn.isEmpty()) {
                        File out = new File(photosDir, fn);
                        copyStreamToFile(zis, out);
                        res.photosCopied++;
                    }
                }

                zis.closeEntry();
                processed++;
                if (cb != null) cb.onProgress("Bestanden", processed, totalEntries);
            }
        }
        return res;
    }



    // -------- helpers --------

    private static void copyStreamToFile(InputStream in, File out) throws Exception {
        File parent = out.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(out))) {
            byte[] buf = new byte[8192]; int r;
            while ((r = in.read(buf)) != -1) bos.write(buf, 0, r);
        }
    }

    private static JSONArray readArray(File f) {
        if (f == null || !f.exists()) return new JSONArray();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = br.readLine()) != null) sb.append(line);
            String s = sb.toString().trim();
            return s.isEmpty() ? new JSONArray() : new JSONArray(s);
        } catch (Exception e) { return new JSONArray(); }
    }

    // VERVANG in NenImporter.java de InputStream-variant door deze:
    private static JSONArray readArrayEntry(java.util.zip.ZipInputStream zis) {
        try {
            StringBuilder sb = new StringBuilder(16 * 1024);
            byte[] buf = new byte[8192];
            int r;
            while ((r = zis.read(buf)) != -1) {           // leest tot einde van de entry
                sb.append(new String(buf, 0, r, java.nio.charset.StandardCharsets.UTF_8));
            }
            String s = sb.toString().trim();
            return s.isEmpty() ? new org.json.JSONArray() : new org.json.JSONArray(s);
        } catch (Exception e) {
            return new org.json.JSONArray();
        }
    }

    private static void writeArray(File f, JSONArray arr) throws Exception {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileOutputStream fos = new FileOutputStream(f)) {
            byte[] bytes = arr.toString(2).getBytes(StandardCharsets.UTF_8);
            fos.write(bytes);
        }
    }

    private static int mergeLocations(JSONArray existing, JSONArray incoming) throws Exception {
        int updated = 0;
        for (int i = 0; i < incoming.length(); i++) {
            JSONObject in = incoming.optJSONObject(i);
            if (in == null) continue;
            String id = in.optString("id", "");
            if (id.isEmpty()) continue;

            int idx = indexOfById(existing, id);
            if (idx >= 0) {
                JSONObject ex = existing.optJSONObject(idx);
                if (ex != null) {
                    for (Iterator<String> it = in.keys(); it.hasNext(); ) {
                        String k = it.next();
                        ex.put(k, in.opt(k));
                    }
                    updated++;
                }
            } else {
                existing.put(in);
                updated++;
            }
        }
        return updated;
    }

    private static void mergeBoards(JSONArray existing, JSONArray incoming) throws Exception {
        for (int i = 0; i < incoming.length(); i++) {
            JSONObject in = incoming.optJSONObject(i);
            if (in == null) continue;
            String id = in.optString("id", "");
            if (!id.isEmpty()) {
                int idx = indexOfById(existing, id);
                if (idx >= 0) {
                    JSONObject ex = existing.optJSONObject(idx);
                    if (ex != null) {
                        for (Iterator<String> it = in.keys(); it.hasNext(); ) {
                            String k = it.next();
                            ex.put(k, in.opt(k));
                        }
                    }
                } else {
                    existing.put(in);
                }
            } else {
                existing.put(in);
            }
        }
    }

    private static void appendAll(JSONArray existing, JSONArray incoming) {
        for (int i = 0; i < incoming.length(); i++) existing.put(incoming.opt(i));
    }

    private static int indexOfById(JSONArray arr, String id) {
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            if (id.equals(o.optString("id", ""))) return i;
        }
        return -1;
    }
// --- VARIANT- & DEDUP-HELPERS ---

    private static boolean nameIs(String s, String... cands) {
        for (String x : cands) if (s.equalsIgnoreCase(x)) return true;
        return false;
    }
    private static boolean nameStarts(String s, String... prefixes) {
        String low = s.toLowerCase(java.util.Locale.ROOT);
        for (String p : prefixes) if (low.startsWith(p.toLowerCase(java.util.Locale.ROOT))) return true;
        return false;
    }
    /** Haal <loc> en <board> uit namen als measure_<loc>_<board>.json / defects_<loc>_<board>.json / metingen_/gebreken_/defecten_ */
    @Nullable
    private static String[] extractLocBoardFromName(String n) {
        int us1 = n.indexOf('_'); if (us1 < 0) return null;
        int us2 = n.lastIndexOf('_'); if (us2 <= us1) return null;
        int dot = n.lastIndexOf('.'); if (dot < 0 || dot <= us2) return null;
        String loc = n.substring(us1 + 1, us2);
        String board = n.substring(us2 + 1, dot);
        if (loc.isEmpty() || board.isEmpty()) return null;
        return new String[]{ loc, board };
    }
    /** Haal alleen <loc> uit namen als measure_<loc>.json / metingen_<loc>.json */
    @Nullable
    private static String extractLocOnly(String n) {
        int us = n.indexOf('_'); if (us < 0) return null;
        int dot = n.lastIndexOf('.'); if (dot < 0 || dot <= us) return null;
        String loc = n.substring(us + 1, dot);
        return loc.isEmpty() ? null : loc;
    }

    /** Merge op id: records met gelijke 'id' worden vervangen i.p.v. gedupliceerd. */
    private static void mergeById(JSONArray existing, JSONArray incoming) {
        java.util.LinkedHashMap<String, JSONObject> map = new java.util.LinkedHashMap<>();
        int noId = 0;
        for (int i = 0; i < existing.length(); i++) {
            JSONObject o = existing.optJSONObject(i); if (o == null) continue;
            String id = o.optString("id", null);
            map.put(id == null || id.isEmpty() ? "__noid_existing__" + (noId++) : id, o);
        }
        for (int i = 0; i < incoming.length(); i++) {
            JSONObject o = incoming.optJSONObject(i); if (o == null) continue;
            String id = o.optString("id", null);
            map.put(id == null || id.isEmpty() ? "__noid_incoming__" + (noId++) : id, o); // replace bij gelijke id
        }
        // schrijf terug naar 'existing'
        while (existing.length() > 0) existing.remove(existing.length() - 1);
        for (JSONObject o : map.values()) existing.put(o);
    }
    private NenImporter() {}
}
