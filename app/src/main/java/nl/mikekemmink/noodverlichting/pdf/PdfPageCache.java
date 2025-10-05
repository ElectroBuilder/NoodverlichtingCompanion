package nl.mikekemmink.noodverlichting.pdf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfRenderer;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.LruCache;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public final class PdfPageCache {

    // ~1/8 van max geheugen voor memory-cache
    private static final LruCache<String, Bitmap> MEM = new LruCache<String, Bitmap>(
            (int) (Runtime.getRuntime().maxMemory() / 8 / 1024)) {
        @Override protected int sizeOf(@NonNull String key, @NonNull Bitmap value) {
            return value.getByteCount() / 1024; // in KB
        }
    };

    // Simpele lock-per-key om dubbel renderen te voorkomen
    private static final ConcurrentHashMap<String, Object> LOCKS = new ConcurrentHashMap<>();

    // Houd de cache op max 150 MB (pas aan naar wens)
    private static final long MAX_DISK_CACHE_BYTES = 150L * 1024L * 1024L;

    private PdfPageCache() {}

    /** Haal bitmap uit cache, of render en schrijf naar cache. */
    public static Bitmap getOrRender(Context ctx, @NonNull File pdfFile, int pageIndex, int targetWidthPx) throws Exception {
        int widthBucket = bucketWidth(targetWidthPx);  // width quantization voor betere hit-rate
        String key = key(pdfFile, pageIndex, widthBucket);

        // 1) Memory-cache
        Bitmap mem = MEM.get(key);
        if (mem != null && !mem.isRecycled()) return mem;

        File diskFile = diskFile(ctx, key);
        Object lock = LOCKS.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            // 2) Disk-cache (dubbele check na lock)
            if (diskFile.exists() && diskFile.length() > 0) {
                Bitmap bmp = BitmapFactory.decodeFile(diskFile.getAbsolutePath());
                if (bmp != null) {
                    MEM.put(key, bmp);
                    return bmp;
                }
            }

            // 3) Render met PdfRenderer
            try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
                 PdfRenderer renderer = new PdfRenderer(pfd);
                 PdfRenderer.Page page = renderer.openPage(pageIndex)) {

                float scale = (float) widthBucket / (float) page.getWidth();
                int w = Math.max(1, widthBucket);
                int h = Math.max(1, Math.round(page.getHeight() * scale));

                Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                // 4) Schrijf naar disk (WEBP voor snelheid/ruimte; PNG kan ook)
                ensureParent(diskFile);
                try (FileOutputStream out = new FileOutputStream(diskFile)) {
                    if (Build.VERSION.SDK_INT >= 30) {
                        bmp.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, out);
                    } else {
                        bmp.compress(Bitmap.CompressFormat.WEBP, 90, out);
                    }
                }

                // 5) In memory-cache
                MEM.put(key, bmp);

                // 6) Budget bewaken (best-effort)
                trimDiskCache(ctx);

                return bmp;
            } finally {
                // lock opruimen (best effort)
                LOCKS.remove(key);
            }
        }
    }

    /** Vooraf genereren (prefetch), handig na import of op splash. */
    public static void prefetch(Context ctx, @NonNull File pdfFile, int[] pages, int targetWidthPx) {
        for (int p : pages) {
            try { getOrRender(ctx, pdfFile, p, targetWidthPx); } catch (Throwable ignore) {}
        }
    }

    /** Verwijder hele cache (bijv. als PDFs vervangen worden). */
    public static void clearDiskCache(Context ctx) {
        File dir = cacheDir(ctx);
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) { // best effort
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    // ---------- helpers ----------

    private static int bucketWidth(int w) {
        // Rond af op 128px buckets om onnodige varianten te vermijden
        return ((w + 127) / 128) * 128;
    }

    private static File cacheDir(Context ctx) {
        File dir = new File(ctx.getCacheDir(), "pdfcache");
        if (!dir.exists()) //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        return dir;
    }

    private static File diskFile(Context ctx, String key) {
        return new File(cacheDir(ctx), key + ".webp");
    }

    private static void ensureParent(File f) {
        File p = f.getParentFile();
        if (p != null && !p.exists()) //noinspection ResultOfMethodCallIgnored
            p.mkdirs();
    }

    private static void trimDiskCache(Context ctx) {
        File dir = cacheDir(ctx);
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) return;

        long total = 0;
        for (File f : files) total += f.length();
        if (total <= MAX_DISK_CACHE_BYTES) return;

        // Oudste eerst weggooien
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        int i = 0;
        while (total > MAX_DISK_CACHE_BYTES && i < files.length) {
            long len = files[i].length();
            //noinspection ResultOfMethodCallIgnored
            files[i].delete();
            total -= len;
            i++;
        }
    }

    private static String key(File pdfFile, int page, int width) throws Exception {
        // Path + lastModified + length => nieuw cachebestand als bron-PDF wijzigt
        String id = (pdfFile.getAbsolutePath() + "|" + pdfFile.lastModified() + "|" + pdfFile.length()
                + "|" + page + "|" + width);
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] hash = md.digest(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format(Locale.ROOT, "%02x", b));
        return sb.toString();
    }
}