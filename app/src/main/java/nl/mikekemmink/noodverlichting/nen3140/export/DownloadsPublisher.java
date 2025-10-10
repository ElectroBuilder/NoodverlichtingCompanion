package nl.mikekemmink.noodverlichting.nen3140.export;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.FileInputStream;

/**
 * Publiceert een ZIP bestand in de systeem-Downloads map.
 * - API 29+: via MediaStore (geen extra permissies nodig).
 * - API <29: kopie naar Environment.DIRECTORY_DOWNLOADS (vereist WRITE_EXTERNAL_STORAGE).
 */
public final class DownloadsPublisher {

    /**
     * @param ctx          Context
     * @param srcZip       Bestaand ZIP-bestand (bijv. in getExternalFilesDir(...)/export)
     * @param subFolder    Submap onder Downloads (bijv. "NEN3140"), of null voor root van Downloads
     * @param displayName  Bestandsnaam die de gebruiker ziet (bijv. "Werkplaats Noord_20251010_161955.zip")
     * @return Uri naar het bestand in Downloads (of null bij fallback <29)
     */
    @Nullable
    public static Uri saveZipToDownloads(Context ctx, File srcZip, @Nullable String subFolder, String displayName) throws Exception {
        if (srcZip == null || !srcZip.exists()) throw new IllegalArgumentException("Bron ZIP ontbreekt: " + srcZip);

        if (Build.VERSION.SDK_INT >= 29) {
            return saveViaMediaStore(ctx, srcZip, subFolder, displayName);
        } else {
            // Legacy: kopieer naar openbare Downloads
            File outDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (subFolder != null && !subFolder.trim().isEmpty()) {
                outDir = new File(outDir, subFolder.trim());
            }
            if (!outDir.exists()) outDir.mkdirs();
            File dst = new File(outDir, displayName);
            copyFile(srcZip, dst);
            return null; // oudere APIs hebben geen MediaStore Uri nodig
        }
    }

    @RequiresApi(29)
    private static Uri saveViaMediaStore(Context ctx, File srcZip, @Nullable String subFolder, String displayName) throws Exception {
        ContentResolver cr = ctx.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, displayName);
        values.put(MediaStore.Downloads.MIME_TYPE, "application/zip");

        // Zet in Downloads/NEN3140 of direct in Downloads
        String rel = Environment.DIRECTORY_DOWNLOADS + (subFolder != null && !subFolder.trim().isEmpty()
                ? File.separator + subFolder.trim() : "");
        values.put(MediaStore.Downloads.RELATIVE_PATH, rel);
        // (optioneel) eerst pending, na schrijven clearen
        values.put(MediaStore.Downloads.IS_PENDING, 1);

        Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IllegalStateException("Kon niet in Downloads aanmaken");

        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(srcZip));
             OutputStream os = cr.openOutputStream(uri);
             BufferedOutputStream out = new BufferedOutputStream(os)) {

            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
        }

        // IS_PENDING -> 0
        ContentValues done = new ContentValues();
        done.put(MediaStore.Downloads.IS_PENDING, 0);
        cr.update(uri, done, null, null);

        return uri;
    }

    private static void copyFile(File src, File dst) throws Exception {
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(src));
             BufferedOutputStream out = new BufferedOutputStream(new java.io.FileOutputStream(dst))) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
        }
    }

    private DownloadsPublisher() {}
}