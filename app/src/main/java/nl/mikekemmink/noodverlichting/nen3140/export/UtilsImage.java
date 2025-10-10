package nl.mikekemmink.noodverlichting.nen3140.export;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;

import androidx.exifinterface.media.ExifInterface;

import java.io.IOException;

final class UtilsImage {

    /** Laad bitmap snel, schaalt naar maxLongEdgePx en past EXIF-oriëntatie toe. */
    static Bitmap loadScaledWithOrientation(java.io.File src, int maxLongEdgePx) throws IOException {
        // 1) bounds
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(src.getAbsolutePath(), o);
        int w = Math.max(1, o.outWidth), h = Math.max(1, o.outHeight);

        // 2) inSampleSize
        int sample = 1, longEdge = Math.max(w, h);
        while ((longEdge / sample) > maxLongEdgePx) sample <<= 1;

        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        o2.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bmp = BitmapFactory.decodeFile(src.getAbsolutePath(), o2);
        if (bmp == null) throw new IOException("Decode mislukt: " + src);

        // 3) exacte schaal
        float scale = Math.min(1f, maxLongEdgePx / (float) Math.max(bmp.getWidth(), bmp.getHeight()));
        if (scale < 1f) {
            int nw = Math.round(bmp.getWidth() * scale);
            int nh = Math.round(bmp.getHeight() * scale);
            Bitmap scaled = Bitmap.createScaledBitmap(bmp, nw, nh, true);
            if (scaled != bmp) bmp.recycle();
            bmp = scaled;
        }

        // 4) EXIF-oriëntatie
        try {
            ExifInterface exif = new ExifInterface(src.getAbsolutePath());
            int tag = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
            Matrix m = new Matrix();
            switch (tag) {
                case ExifInterface.ORIENTATION_ROTATE_90:  m.postRotate(90);  break;
                case ExifInterface.ORIENTATION_ROTATE_180: m.postRotate(180); break;
                case ExifInterface.ORIENTATION_ROTATE_270: m.postRotate(270); break;
                default: /* as-is */
            }
            if (!m.isIdentity()) {
                Bitmap rot = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
                if (rot != bmp) bmp.recycle();
                bmp = rot;
            }
        } catch (Throwable ignore) {}

        return bmp;
    }

    /** Schrijf als JPEG met opgegeven kwaliteit. */
    static void writeJpeg(Bitmap bmp, java.io.OutputStream os, int quality) throws IOException {
        if (!bmp.compress(Bitmap.CompressFormat.JPEG, quality, os)) {
            throw new IOException("JPEG compress mislukt");
        }
    }

    private UtilsImage() {}
}