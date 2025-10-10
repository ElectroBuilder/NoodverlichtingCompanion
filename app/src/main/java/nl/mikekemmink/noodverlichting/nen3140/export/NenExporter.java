package nl.mikekemmink.noodverlichting.nen3140.export;

import android.content.Context;
import androidx.annotation.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class NenExporter {

    public static File exportToZip(Context ctx, File outDirOrNull, ExportOptions opts) throws Exception {
        return exportToZip(ctx, outDirOrNull, opts, null);
    }
    public static File exportToZip(Context ctx, File outDirOrNull) throws Exception {
        return exportToZip(ctx, outDirOrNull, (ExportOptions) null);
    }

    public static File exportToZip(Context ctx,
                                   File outDirOrNull,
                                   @Nullable ExportOptions opts,
                                   @Nullable ProgressCallback cb) throws Exception {
        if (opts == null) opts = new ExportOptions();
        File baseDir = new File(ctx.getFilesDir(), "nen3140");
        if (!baseDir.exists()) throw new java.io.FileNotFoundException("nen3140 baseDir ontbreekt: " + baseDir);

        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File outDir = (outDirOrNull != null ? outDirOrNull : new File(ctx.getExternalFilesDir(null), "export"));
        if (!outDir.exists()) outDir.mkdirs();

        String baseName = resolveBaseName(ctx, opts, baseDir);
        String fileName = safeFileName(baseName) + "_" + stamp + ".zip";
        File outZip = new File(outDir, fileName);

        JSONObject manifest = new JSONObject();
        manifest.put("schema", 2);
        manifest.put("exported_at", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date()));
        manifest.put("source_app", "Companion-Android");
        manifest.put("modules", new JSONArray().put("nen3140"));

        JSONArray files = new JSONArray();
        Map<String, String> photoWritten = new HashMap<>();

        Set<File> boardPhotoFiles = new LinkedHashSet<>();
        Set<String> refPhotoNames = new LinkedHashSet<>();

        File[] top = baseDir.listFiles();
        if (top != null) {
            for (File f : top) {
                String name = f.getName();
                if (!(f.isFile() && name.startsWith("boards_") && name.endsWith(".json"))) continue;
                String locationId = name.substring("boards_".length(), name.length() - ".json".length());
                if (!opts.locationIds.isEmpty() && !opts.locationIds.contains(locationId)) continue;

                JSONArray boards = readArray(f);
                for (int i = 0; i < boards.length(); i++) {
                    JSONObject b = boards.optJSONObject(i);
                    if (b == null) continue;
                    String pb = b.optString("photoBoardPath", null);
                    if (pb != null && !pb.isEmpty()) {
                        File src = resolveFile(pb);
                        if (src != null && src.isFile()) boardPhotoFiles.add(src);
                    }
                    String pi = b.optString("photoInfoPath", null);
                    if (pi != null && !pi.isEmpty()) {
                        File src = resolveFile(pi);
                        if (src != null && src.isFile()) boardPhotoFiles.add(src);
                    }
                }
            }

            String[] measureCandidates = new String[] {
                "measure_", "measurement_", "metingen_", "measure.json", "measurement.json", "measurements.json", "metingen.json"
            };
            String[] defectCandidates = new String[] {
                "defects_", "gebreken_", "defecten_", "defects.json", "gebreken.json", "defecten.json"
            };
            collectReferencedPhotosFlexible(baseDir, opts, refPhotoNames, measureCandidates);
            collectReferencedPhotosFlexible(baseDir, opts, refPhotoNames, defectCandidates);
        }

        int totalPhotos = boardPhotoFiles.size() + refPhotoNames.size();
        int processedPhotos = 0;
        if (cb != null) cb.onProgress("Scannen", 0, totalPhotos);

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outZip))) {
            zos.setLevel(Deflater.BEST_SPEED);

            File locFile = new File(baseDir, "locations.json");
            if (locFile.exists() && locFile.isFile()) {
                JSONArray locs = readArray(locFile);
                if (opts.locationIds.isEmpty()) {
                    addFile(zos, files, locFile, "nen3140/locations.json");
                } else {
                    JSONArray filtered = new JSONArray();
                    for (int i = 0; i < locs.length(); i++) {
                        JSONObject o = locs.optJSONObject(i);
                        if (o == null) continue;
                        String id = o.optString("id", "");
                        if (opts.locationIds.contains(id)) filtered.put(o);
                    }
                    addBytes(zos, files, filtered.toString(2).getBytes(StandardCharsets.UTF_8), "nen3140/locations.json");
                }
            }

            if (top != null) {
                for (File f : top) {
                    if (!f.isFile()) continue;
                    String n = f.getName();
                    boolean isMeasurement = isCandidate(n,
                        new String[]{"measure_", "measurement_", "metingen_"},
                        new String[]{"measure.json", "measurement.json", "measurements.json", "metingen.json"});
                    boolean isDefect = isCandidate(n,
                        new String[]{"defects_", "gebreken_", "defecten_"},
                        new String[]{"defects.json", "gebreken.json", "defecten.json"});
                    if (!(isMeasurement || isDefect)) continue;

                    if (opts.locationIds.isEmpty()) {
                        addFile(zos, files, f, "nen3140/" + n);
                    } else {
                        String suffix = extractSuffixId(n);
                        String locFromName = firstTokenOrSelf(suffix);
                        if (locFromName != null && opts.locationIds.contains(locFromName)) {
                            addFile(zos, files, f, "nen3140/" + n);
                        } else {
                            JSONArray src = readArray(f);
                            JSONArray filtered = new JSONArray();
                            for (int i = 0; i < src.length(); i++) {
                                JSONObject o = src.optJSONObject(i);
                                if (o == null) continue;
                                String lid = o.optString("locationId", o.optString("locatieId", ""));
                                if (opts.locationIds.contains(lid)) filtered.put(o);
                            }
                            if (filtered.length() > 0) {
                                addBytes(zos, files, filtered.toString(2).getBytes(StandardCharsets.UTF_8), "nen3140/" + n);
                            }
                        }
                    }
                }
            }

            if (top != null) {
                for (File f : top) {
                    String name = f.getName();
                    if (!(f.isFile() && name.startsWith("boards_") && name.endsWith(".json"))) continue;
                    String locationId = name.substring("boards_".length(), name.length() - ".json".length());
                    if (!opts.locationIds.isEmpty() && !opts.locationIds.contains(locationId)) continue;

                    JSONArray boards = readArray(f);
                    for (int i = 0; i < boards.length(); i++) {
                        if (cb != null && cb.isCancelled()) throw new InterruptedException("Geannuleerd");
                        JSONObject b = boards.optJSONObject(i);
                        if (b == null) continue;
                        String boardId = b.optString("id", String.valueOf(i));

                        String pb = b.optString("photoBoardPath", null);
                        if (pb != null && !pb.isEmpty()) {
                            File src = resolveFile(pb);
                            if (src != null && src.isFile()) {
                                String outName = boardPhotoName(locationId, boardId, "board");
                                String arc = "nen3140/photos/" + outName;
                                writePhotoUsingCache(ctx, zos, files, photoWritten, src, arc, opts);
                                b.put("photoBoardPath", outName);
                                processedPhotos++; if (cb != null) cb.onProgress("Foto's", processedPhotos, totalPhotos);
                            }
                        }
                        String pi = b.optString("photoInfoPath", null);
                        if (pi != null && !pi.isEmpty()) {
                            File src = resolveFile(pi);
                            if (src != null && src.isFile()) {
                                String outName = boardPhotoName(locationId, boardId, "info");
                                String arc = "nen3140/photos/" + outName;
                                writePhotoUsingCache(ctx, zos, files, photoWritten, src, arc, opts);
                                b.put("photoInfoPath", outName);
                                processedPhotos++; if (cb != null) cb.onProgress("Foto's", processedPhotos, totalPhotos);
                            }
                        }
                    }
                    addBytes(zos, files, boards.toString(2).getBytes(StandardCharsets.UTF_8), "nen3140/" + name);
                }
            }

            File photosDir = new File(baseDir, "photos");
            for (String fn : refPhotoNames) {
                if (cb != null && cb.isCancelled()) throw new InterruptedException("Geannuleerd");
                File src = findFileByNameRecursive(photosDir, fn);
                if (src == null) {
                    File tryAbs = resolveFile(fn);
                    if (tryAbs != null && tryAbs.exists()) src = tryAbs;
                }
                if (src != null && src.exists() && src.isFile()) {
                    String nameNoExt = src.getName().replaceAll("\\.[A-Za-z0-9]+$", "");
                    String arc = "nen3140/photos/" + nameNoExt + ".jpg";
                    writePhotoUsingCache(ctx, zos, files, photoWritten, src, arc, opts);
                }
                processedPhotos++; if (cb != null) cb.onProgress("Foto's", processedPhotos, totalPhotos);
            }

            manifest.put("files", files);
            addBytes(zos, files, manifest.toString(2).getBytes(StandardCharsets.UTF_8), "manifest.json");
        }
        return outZip;
    }

    private static String extractSuffixId(String fileName) {
        if (!fileName.endsWith(".json")) return null;
        int us = fileName.indexOf('_');
        if (us < 0) return null;
        return fileName.substring(us + 1, fileName.length() - ".json".length());
    }
    private static String firstTokenOrSelf(@Nullable String s) {
        if (s == null) return null;
        int us = s.indexOf('_');
        return (us >= 0) ? s.substring(0, us) : s;
    }
    private static boolean isCandidate(String n, String[] prefixes, String[] exacts) {
        if (n.endsWith(".json")) {
            for (String p : prefixes) if (n.startsWith(p)) return true;
            for (String e : exacts) if (n.equalsIgnoreCase(e)) return true;
        }
        return false;
    }
    private static JSONArray readArray(File f) {
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = br.readLine()) != null) sb.append(line);
            String s = sb.toString().trim();
            return s.isEmpty() ? new JSONArray() : new JSONArray(s);
        } catch (Exception e) { return new JSONArray(); }
    }
    private static void addFile(ZipOutputStream zos, JSONArray files, File src, String arc) throws Exception {
        zos.putNextEntry(new ZipEntry(arc));
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(src))) {
            byte[] buf = new byte[8192]; int r; while ((r = in.read(buf)) != -1) zos.write(buf, 0, r);
        }
        zos.closeEntry();
        JSONObject fe = new JSONObject();
        fe.put("path", arc); fe.put("bytes", src.length());
        files.put(fe);
    }
    private static void addBytes(ZipOutputStream zos, JSONArray files, byte[] payload, String arc) throws Exception {
        zos.putNextEntry(new ZipEntry(arc));
        zos.write(payload);
        zos.closeEntry();
        JSONObject fe = new JSONObject();
        fe.put("path", arc); fe.put("bytes", payload.length);
        files.put(fe);
    }
    private static void writeZipEntryFromDisk(ZipOutputStream zos, JSONArray files, File file, String arc) throws Exception {
        zos.putNextEntry(new ZipEntry(arc));
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buf = new byte[8192]; int r; while ((r = in.read(buf)) != -1) zos.write(buf, 0, r);
        }
        zos.closeEntry();
        JSONObject fe = new JSONObject();
        fe.put("path", arc);
        fe.put("bytes", file.length());
        files.put(fe);
    }
    private static void writePhotoUsingCache(Context ctx, ZipOutputStream zos, JSONArray files,
                                             Map<String, String> photoWritten,
                                             File src, String arc, ExportOptions opts) throws Exception {
        String key = src.getAbsolutePath();
        if (photoWritten.containsKey(key)) return;
        if (opts.useDiskCache) {
            File cached = PhotoDiskCache.find(ctx, src, opts);
            if (cached == null) cached = PhotoDiskCache.ensure(ctx, src, opts);
            writeZipEntryFromDisk(zos, files, cached, arc);
        } else {
            zos.putNextEntry(new ZipEntry(arc));
            try {
                android.graphics.Bitmap bmp = UtilsImage.loadScaledWithOrientation(src, opts.maxLongEdgePx);
                UtilsImage.writeJpeg(bmp, zos, opts.jpegQuality);
                bmp.recycle();
            } finally {
                zos.closeEntry();
            }
            JSONObject fe = new JSONObject();
            fe.put("path", arc);
            fe.put("bytes", src.length());
            files.put(fe);
        }
        photoWritten.put(key, arc);
    }
    private static File findFileByNameRecursive(File dir, String name) {
        if (dir == null || !dir.exists()) return null;
        Deque<File> stack = new ArrayDeque<>();
        stack.push(dir);
        while (!stack.isEmpty()) {
            File d = stack.pop();
            File[] kids = d.listFiles();
            if (kids == null) continue;
            for (File k : kids) {
                if (k.isDirectory()) { stack.push(k); continue; }
                if (k.getName().equalsIgnoreCase(name)) return k;
            }
        }
        return null;
    }
    private static String boardPhotoName(String locationId, String boardId, String kind) {
        return "board_" + safe(locationId) + "_" + safe(boardId) + "_" + safe(kind) + ".jpg";
    }
    private static String safe(String s) {
        if (s == null || s.isEmpty()) return "x";
        return s.replaceAll("[^A-Za-z0-9_\\-]", "_");
    }
    private static File resolveFile(String pathOrName) {
        if (pathOrName == null || pathOrName.trim().isEmpty()) return null;
        File f = new File(pathOrName);
        if (f.exists()) return f;
        return null;
    }

    private static void collectReferencedPhotosFlexible(File baseDir,
                                                        ExportOptions opts,
                                                        Set<String> out,
                                                        String[] nameCandidates) {
        File[] top = baseDir.listFiles();
        if (top == null) return;

        Set<String> exacts = new HashSet<>();
        for (String c : nameCandidates) if (c.endsWith(".json")) exacts.add(c);

        for (File f : top) {
            String n = f.getName();
            if (!(f.isFile() && isCandidate(n, nameCandidates, exacts.toArray(new String[0])))) continue;

            String suffix = extractSuffixId(n);
            String locFromName = firstTokenOrSelf(suffix);

            JSONArray arr = readArray(f);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;

                boolean include = true;
                if (!opts.locationIds.isEmpty()) {
                    String lid = o.optString("locationId", o.optString("locatieId", ""));
                    if (!lid.isEmpty()) {
                        include = opts.locationIds.contains(lid);
                    } else if (locFromName != null) {
                        include = opts.locationIds.contains(locFromName);
                    } else {
                        include = false;
                    }
                }
                if (!include) continue;
                collectPhotoNamesRecursive(o, out);
            }
        }
    }
    private static void collectPhotoNamesRecursive(Object node, Set<String> out) {
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            for (Iterator<String> it = obj.keys(); it.hasNext(); ) {
                String key = it.next();
                Object val = obj.opt(key);
                String kl = key.toLowerCase(Locale.ROOT);
                boolean looksLikePhotoKey = kl.contains("photo") || kl.contains("foto") || kl.contains("image") || kl.contains("afbeeld");
                if (looksLikePhotoKey) {
                    if (val instanceof String) addIfImageName((String) val, out);
                    else if (val instanceof JSONArray || val instanceof JSONObject) collectPhotoNamesRecursive(val, out);
                } else {
                    if (val instanceof JSONArray || val instanceof JSONObject) collectPhotoNamesRecursive(val, out);
                }
            }
        } else if (node instanceof JSONArray) {
            JSONArray arr = (JSONArray) node;
            for (int i = 0; i < arr.length(); i++) {
                Object v = arr.opt(i);
                if (v instanceof String) addIfImageName((String) v, out);
                else if (v instanceof JSONArray || v instanceof JSONObject) collectPhotoNamesRecursive(v, out);
            }
        }
    }
    private static void addIfImageName(String path, Set<String> out) {
        if (path == null || path.trim().isEmpty()) return;
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
         || lower.endsWith(".webp") || lower.endsWith(".heic") || lower.endsWith(".bmp")) {
            out.add(new File(path).getName());
        }
    }

    public interface ProgressCallback {
        void onProgress(String phase, int current, int total);
        boolean isCancelled();
    }
    private static String resolveBaseName(Context ctx, ExportOptions opts, File baseDir) {
        if (opts.outputBaseName != null && !opts.outputBaseName.trim().isEmpty()) return opts.outputBaseName.trim();
        if (opts.locationIds != null && opts.locationIds.size() == 1) {
            String wantedId = opts.locationIds.iterator().next();
            File locFile = new File(baseDir, "locations.json");
            JSONArray locs = readArray(locFile);
            for (int i = 0; i < locs.length(); i++) {
                JSONObject o = locs.optJSONObject(i);
                if (o == null) continue;
                String id = o.optString("id", "");
                if (wantedId.equals(id)) {
                    String nm = o.optString("name", o.optString("naam", ""));
                    if (nm != null && !nm.trim().isEmpty()) return nm.trim();
                    break;
                }
            }
            return wantedId;
        }
        return "nen3140_export";
    }
    private static String safeFileName(String s) {
        // - verbied karakters: \ / : * ? " < > en control characters
        String cleaned = s.replaceAll("[\\\\/:*?\\\"<>\\p{Cntrl}]", "_")
                .replaceAll("\\s+", " ") // normaliseer whitespace
                .trim();
        // - voorkom extreem lange namen
        if (cleaned.length() > 80) cleaned = cleaned.substring(0, 80);
        // - lege fallback
        if (cleaned.isEmpty()) cleaned = "nen3140_export";
        return cleaned;
    }
}
