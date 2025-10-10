
package nl.mikekemmink.noodverlichting.nen3140.export;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;

final class PhotoDiskCache {

    static File getCacheDir(Context ctx, ExportOptions opts) {
        File root = new File(ctx.getCacheDir(), "nen3140_imgcache");
        File dir = new File(root, "edge" + opts.maxLongEdgePx + "_q" + opts.jpegQuality);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    static String keyOf(File src, ExportOptions opts) {
        String seed = src.getAbsolutePath() + "|" + src.lastModified() + "|" + src.length()
                + "|" + opts.maxLongEdgePx + "|" + opts.jpegQuality;
        return sha256(seed).substring(0, 40);
    }

    static File find(Context ctx, File src, ExportOptions opts) {
        File dir = getCacheDir(ctx, opts);
        String key = keyOf(src, opts);
        File f = new File(dir, key + ".jpg");
        return f.exists() ? f : null;
    }

    /** Zorgt dat er een cachebestand is; maakt het indien nodig (atomic write) */
    static File ensure(Context ctx, File src, ExportOptions opts) throws Exception {
        File dir = getCacheDir(ctx, opts);
        String key = keyOf(src, opts);
        File out = new File(dir, key + ".jpg");
        if (out.exists()) return out;

        File tmp = new File(dir, key + ".part");
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            android.graphics.Bitmap bmp = UtilsImage.loadScaledWithOrientation(src, opts.maxLongEdgePx);
            try {
                UtilsImage.writeJpeg(bmp, fos, opts.jpegQuality);
            } finally {
                bmp.recycle();
            }
        } catch (IOException ioe) {
            tmp.delete();
            throw ioe;
        }
        if (!tmp.renameTo(out)) {
            tmp.delete();
            throw new IOException("Kon cachebestand niet hernoemen: " + tmp + " -> " + out);
        }
        return out;
    }

    static void clearAll(Context ctx) {
        File root = new File(ctx.getCacheDir(), "nen3140_imgcache");
        deleteRecursive(root);
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        f.delete();
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : dig) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return Integer.toHexString(s.hashCode()); }
    }

    private PhotoDiskCache() {}
}
