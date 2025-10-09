package nl.mikekemmink.noodverlichting.nen3140;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageView;
import androidx.annotation.Nullable;
import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.ui.BaseToolbarActivity;
import java.io.File;
import static nl.mikekemmink.noodverlichting.nen3140.adapters.NenDefectListAdapter.*; // voor decodeThumbWithRotation, dp als je die wilt hergebruiken

public class PhotoPreviewActivity extends BaseToolbarActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.activity_photo_preview);
        setTitle("Foto");
        setUpEnabled(true);

        String fileName = getIntent().getStringExtra("photo");
        if (fileName == null) {
            finish();
            return;
        }

        File img = new File(getFilesDir(), "nen3140/photos/" + fileName);
        ImageView iv = findViewById(R.id.imgFull);

        // Decode passend aan schermgrootte
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        Bitmap bmp = decodeThumbWithRotation(img, w, h);
        iv.setImageBitmap(bmp);
    }
}