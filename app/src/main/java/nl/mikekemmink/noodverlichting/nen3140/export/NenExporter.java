package nl.mikekemmink.noodverlichting.nen3140.export;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.security.MessageDigest;

public final class NenExporter {

    private static String sha256OfFile(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[8192]; int r;
            while ((r = in.read(buf)) != -1) md.update(buf, 0, r);
        }
        byte[] dig = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : dig) sb.append(String.format(Locale.US, "%02x", b));
        return sb.toString();
    }

    private static String extOf(String pathOrName) {
        if (pathOrName == null) return "";
        int i = pathOrName.lastIndexOf('.');
        String e = (i >= 0 ? pathOrName.substring(i) : "");
        if (e.isEmpty()) e = ".jpg";
        return e.toLowerCase(Locale.ROOT);
    }
    private static String boardPhotoName(String locationId, String boardId, String kind, String srcPath) {
        return "board_" + locationId + "_" + boardId + "_" + kind + extOf(srcPath);
    }

    public static File exportToZip(Context ctx, File outDirOrNull) throws Exception {
        File baseDir = new File(ctx.getFilesDir(), "nen3140");   // sluit aan op NenStorage  [1](https://hanabonline-my.sharepoint.com/personal/m_kemmink_hanab_nl/Documents/Microsoft%20Copilot%20Chat-bestanden/NenMeasurement.java)
        if (!baseDir.exists())
            throw new FileNotFoundException("nen3140 baseDir ontbreekt: " + baseDir);

        String stamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File outDir = (outDirOrNull != null ? outDirOrNull : new File(ctx.getExternalFilesDir(null), "export"));
        if (!outDir.exists()) outDir.mkdirs();
        File outZip = new File(outDir, "nen3140_export_" + stamp + ".zip");

        JSONObject manifest = new JSONObject();
        manifest.put("schema", 1);
        manifest.put("exported_at", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date()));
        manifest.put("source_app", "Companion-Android");
        manifest.put("modules", new JSONArray().put("nen3140"));
        JSONArray files = new JSONArray();

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outZip))) {

            // ---- 1) locations.json en overige top-level json direct meenemen
            File[] top = baseDir.listFiles();   // bevat o.a. locations.json, boards_*.json, measure_*.json, defects_*.json  [1](https://hanabonline-my.sharepoint.com/personal/m_kemmink_hanab_nl/Documents/Microsoft%20Copilot%20Chat-bestanden/NenMeasurement.java)
            if (top != null) {
                for (File f : top) {
                    String n = f.getName();
                    if (!f.isFile()) continue;
                    if (n.equals("locations.json") || (n.startsWith("measure_") && n.endsWith(".json"))
                            || (n.startsWith("defects_") && n.endsWith(".json"))) {
                        addFile(zos, files, f, "nen3140/" + n);
                    }
                }
            }

            // ---- 2) boards_*.json: normaliseren + board-foto’s kopiëren
            if (top != null) {
                for (File f : top) {
                    String name = f.getName();
                    if (!(f.isFile() && name.startsWith("boards_") && name.endsWith(".json"))) continue;

                    // 2a) inlezen
                    JSONArray boards = readArray(f);
                    String locationId = name.substring("boards_".length(), name.length() - ".json".length());

                    // 2b) per board foto's meenemen + paden -> bestandsnamen
                    for (int i = 0; i < boards.length(); i++) {
                        JSONObject b = boards.optJSONObject(i);
                        if (b == null) continue;
                        String boardId = b.optString("id", "");

                        String pb = b.optString("photoBoardPath", null);
                        if (pb != null && !pb.isEmpty()) {
                            File src = new File(pb);
                            if (src.exists()) {
                                String newName = boardPhotoName(locationId, boardId, "board", pb);
                                addStreamed(zos, files, src, "nen3140/photos/" + newName);
                                b.put("photoBoardPath", newName); // normaliseren naar bestandsnaam
                            }
                        }
                        String pi = b.optString("photoInfoPath", null);
                        if (pi != null && !pi.isEmpty()) {
                            File src = new File(pi);
                            if (src.exists()) {
                                String newName = boardPhotoName(locationId, boardId, "info", pi);
                                addStreamed(zos, files, src, "nen3140/photos/" + newName);
                                b.put("photoInfoPath", newName);
                            }
                        }
                    }

                    // 2c) gewijzigde boards_*.json in ZIP
                    byte[] payload = boards.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    addBytes(zos, files, payload, "nen3140/" + name);
                }
            }

            // ---- 3) defectfoto’s (al in files/nen3140/photos/ via storage)  [1](https://hanabonline-my.sharepoint.com/personal/m_kemmink_hanab_nl/Documents/Microsoft%20Copilot%20Chat-bestanden/NenMeasurement.java)
            File photosDir = new File(baseDir, "photos");
            if (photosDir.exists()) {
                Deque<File> stack = new ArrayDeque<>();
                stack.push(photosDir);
                while (!stack.isEmpty()) {
                    File d = stack.pop();
                    File[] kids = d.listFiles();
                    if (kids == null) continue;
                    for (File k : kids) {
                        if (k.isDirectory()) { stack.push(k); continue; }
                        addFile(zos, files, k, "nen3140/photos/" + k.getName());
                    }
                }
            }

            // ---- 4) manifest.json
            manifest.put("files", files);
            byte[] manifestBytes = manifest.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            addBytes(zos, files, manifestBytes, "manifest.json");
        }

        return outZip;
    }

    // ---------- helpers ----------
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
        fe.put("path", arc); fe.put("bytes", src.length()); fe.put("sha256", sha256OfFile(src));
        files.put(fe);
    }
    private static void addStreamed(ZipOutputStream zos, JSONArray files, File src, String arc) throws Exception {
        addFile(zos, files, src, arc);
    }
    private static void addBytes(ZipOutputStream zos, JSONArray files, byte[] payload, String arc) throws Exception {
        zos.putNextEntry(new ZipEntry(arc));
        zos.write(payload);
        zos.closeEntry();
        JSONObject fe = new JSONObject();
        fe.put("path", arc); fe.put("bytes", payload.length); // sha optioneel
        files.put(fe);
    }
}
