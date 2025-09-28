
package nl.mikekemmink.noodverlichting.util;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import nl.mikekemmink.noodverlichting.data.DBField;

public class Exporter {

    /**
     * Schrijf database + foto's naar een ZIP-bestand via een SAF Uri (CreateDocument result).
     */
    public static boolean exportToZip(Context ctx, Uri dest) {
        try {
            ContentResolver cr = ctx.getContentResolver();
            try (OutputStream os = cr.openOutputStream(dest);
                 BufferedOutputStream bos = new BufferedOutputStream(os);
                 ZipOutputStream zos = new ZipOutputStream(bos)) {

                // 1) Database-bestand pad ophalen via PRAGMA database_list
                File dbFile = resolveDbFile(ctx);
                if (dbFile != null && dbFile.exists()) {
                    addFile(zos, dbFile, "database/" + dbFile.getName());
                }

                // 2) Foto-map toevoegen: getExternalFilesDir(null)/export/photos
                File photosDir = new File(ctx.getExternalFilesDir(null), "export/photos");
                if (photosDir.exists()) {
                    File[] files = photosDir.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            if (f.isFile()) {
                                addFile(zos, f, "photos/" + f.getName());
                            }
                        }
                    }
                }

                zos.finish();
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static File resolveDbFile(Context ctx) {
        try {
            SQLiteDatabase db = DBField.getInstance(ctx).getReadableDatabase();
            Cursor c = db.rawQuery("PRAGMA database_list;", null);
            int idxName = c.getColumnIndex("name");
            int idxFile = c.getColumnIndex("file");
            File found = null;
            while (c.moveToNext()) {
                String name = idxName >= 0 ? c.getString(idxName) : null;
                String path = idxFile >= 0 ? c.getString(idxFile) : null;
                if (name != null && name.equalsIgnoreCase("main") && path != null && !path.trim().isEmpty()) {
                    found = new File(path);
                    break;
                }
            }
            c.close();
            return found;
        } catch (Exception e) {
            e.printStackTrace();
            // Fallback: als naam onbekend is, probeer een gangbare naam
            // Pas dit desgewenst aan naar jouw DB-naam
            File f = ctx.getDatabasePath("inspectie.db");
            return f != null && f.exists() ? f : null;
        }
    }

    private static void addFile(ZipOutputStream zos, File file, String entryName) throws Exception {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            ZipEntry e = new ZipEntry(entryName);
            zos.putNextEntry(e);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) zos.write(buf, 0, n);
            zos.closeEntry();
        }
    }
}
