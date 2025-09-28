package nl.mikekemmink.noodverlichting.export;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import org.json.JSONArray;
import org.json.JSONObject;

import nl.mikekemmink.noodverlichting.data.DBField;

public class ExportHelper {
    public static String exportToZip(Context ctx) throws Exception {
        File base = new File(ctx.getExternalFilesDir(null), "export");
        if (!base.exists()) base.mkdirs();

        // 1) Build JSON of pending defects
        SQLiteDatabase db = DBField.getInstance(ctx).getReadableDatabase();
        Cursor c = db.rawQuery("SELECT id, inspectie_id, omschrijving, COALESCE(datum,''), COALESCE(foto_pad,'') FROM gebreken ORDER BY id", null);
        JSONArray arr = new JSONArray();
        while (c.moveToNext()) {
            JSONObject o = new JSONObject();
            o.put("id_local", c.getInt(0));
            o.put("inspectie_id", c.getInt(1));
            o.put("omschrijving", c.getString(2));
            o.put("datum", c.getString(3));
            String foto = c.getString(4);
            if (foto != null && foto.length() > 0) {
                o.put("foto_bestand", new File(foto).getName());
            }
            arr.put(o);
        }
        c.close();

        JSONObject root = new JSONObject();
        root.put("schema", 1);
        root.put("generated_at", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date()));
        root.put("defects", arr);

        File jsonFile = new File(base, "mutaties.json");
        try (FileWriter fw = new FileWriter(jsonFile)) { fw.write(root.toString(2)); }

        // 2) Create ZIP with JSON + photos directory contents
        String zipName = "export_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".zip";
        File zipFile = new File(base, zipName);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            // add JSON
            zos.putNextEntry(new ZipEntry("mutaties.json"));
            try (FileInputStream in = new FileInputStream(jsonFile)) {
                byte[] buf = new byte[8192]; int n; while((n=in.read(buf))>0) zos.write(buf,0,n);
            }
            zos.closeEntry();

            // add photos
            File photosDir = new File(base, "photos");
            if (!photosDir.exists()) photosDir = new File(base, "photos");
            // Also include photos that were saved under export/photos
            File alt = new File(base, "photos");
            File alt2 = new File(base, "../export/photos");
            File imgsDir = new File(ctx.getExternalFilesDir(null), "export/photos");
            if (imgsDir.exists()) {
                for (File f: imgsDir.listFiles()) {
                    if (f.isFile()) {
                        zos.putNextEntry(new ZipEntry("photos/" + f.getName()));
                        try (FileInputStream in = new FileInputStream(f)) { byte[] buf = new byte[8192]; int n; while((n=in.read(buf))>0) zos.write(buf,0,n);} 
                        zos.closeEntry();
                    }
                }
            }
        }
        return zipFile.getAbsolutePath();
    }
}