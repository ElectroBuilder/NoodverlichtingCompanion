
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
                } else if (name.startsWith("nen3140/measure_") && name.endsWith(".json")) {
                    String fileName = name.substring("nen3140/".length());
                    File dest = new File(baseDir, fileName);
                    JSONArray incoming = readArrayEntry(zis);
                    JSONArray existing = readArray(dest);
                    appendAll(existing, incoming);
                    writeArray(dest, existing);
                    res.measureFiles++;
                } else if (name.startsWith("nen3140/defects_") && name.endsWith(".json")) {
                    String fileName = name.substring("nen3140/".length());
                    File dest = new File(baseDir, fileName);
                    JSONArray incoming = readArrayEntry(zis);
                    JSONArray existing = readArray(dest);
                    appendAll(existing, incoming);
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

    private NenImporter() {}
}
