package nl.mikekemmink.noodverlichting.nen3140;

import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageView;
import androidx.annotation.Nullable;
import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.ui.BaseToolbarActivity;
import java.io.File;

import androidx.exifinterface.media.ExifInterface;
import android.graphics.Matrix;
import android.util.DisplayMetrics;


public class PhotoPreviewActivity extends BaseToolbarActivity {

    private ImageView image;
    private ScaleGestureDetector scaleDetector;
    private float scaleFactor = 1f;
    private final Matrix matrix = new Matrix();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.simple_fullscreen_image);
        setUpEnabled(true);

        image = findViewById(R.id.ivFull);
        String path = getIntent().getStringExtra("photoPath");
        if (path != null) {
            // Scherm-afmeting als max om OOM te voorkomen
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int maxDim = Math.max(dm.widthPixels, dm.heightPixels);

            Bitmap bm = loadScaledOrientedBitmap(path, maxDim);
            if (bm != null) {
                image.setAdjustViewBounds(true);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                image.setImageBitmap(bm);
            }
        } else {
            Uri u = getIntent().getData();
            if (u != null) image.setImageURI(u);
        }


        // Pinch to zoom
        scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                scaleFactor *= detector.getScaleFactor();
                scaleFactor = Math.max(0.5f, Math.min(scaleFactor, 5.0f));
                matrix.setScale(scaleFactor, scaleFactor, image.getWidth()/2f, image.getHeight()/2f);
                image.setImageMatrix(matrix);
                image.setScaleType(ImageView.ScaleType.MATRIX);
                return true;
            }
        });

        image.setOnTouchListener((v, ev) -> {
            scaleDetector.onTouchEvent(ev);
            return true;
        });

    }
    private Bitmap loadScaledOrientedBitmap(String filePath, int maxPx) {
        try {
            // bounds
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(filePath, o);

            int inSample = 1;
            int maxOrig = Math.max(o.outWidth, o.outHeight);
            while ((maxOrig / inSample) > maxPx) inSample *= 2;

            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = Math.max(1, inSample);
            o2.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bm = BitmapFactory.decodeFile(filePath, o2);
            if (bm == null) return null;

            // EXIF
            ExifInterface exif = new ExifInterface(filePath);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            Matrix m = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:  m.postRotate(90);  break;
                case ExifInterface.ORIENTATION_ROTATE_180: m.postRotate(180); break;
                case ExifInterface.ORIENTATION_ROTATE_270: m.postRotate(270); break;
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: m.preScale(-1, 1); break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:   m.preScale(1, -1); break;
                case ExifInterface.ORIENTATION_TRANSPOSE:       m.postRotate(90);  m.preScale(-1, 1); break;
                case ExifInterface.ORIENTATION_TRANSVERSE:      m.postRotate(270); m.preScale(-1, 1); break;
                default: return bm;
            }
            return Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
        } catch (Exception e) {
            return null;
        }
    }
}