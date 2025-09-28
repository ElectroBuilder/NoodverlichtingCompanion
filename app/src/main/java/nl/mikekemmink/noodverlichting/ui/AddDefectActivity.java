
package nl.mikekemmink.noodverlichting.ui;

import android.content.ContentValues;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;

import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.data.DBField;

public class AddDefectActivity extends AppCompatActivity {
    public static final String EXTRA_INSPECTIE_ID = "inspectie_id";
    public static final String EXTRA_TITEL        = "titel";

    private static final String STATE_LAST_PHOTO_PATH = "state_last_photo_path";

    private int inspectieId;
    private Spinner sp;
    private ImageView img;
    private Uri lastPhotoUri;
    private File lastPhotoFile;

    private final ActivityResultLauncher<Uri> takePhoto =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
                if (ok != null && ok) { showPhoto(lastPhotoFile); } else { lastPhotoFile = null; lastPhotoUri = null; }
            });

    private final ActivityResultLauncher<String> pickImage =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    try {
                        File dst = getNewPhotoFile();
                        try (InputStream in = getContentResolver().openInputStream(uri);
                             FileOutputStream out = new FileOutputStream(dst)) {
                            byte[] buf = new byte[8192]; int n; while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        }
                        lastPhotoFile = dst; showPhoto(dst);
                    } catch (Exception e) { Toast.makeText(this, "Import foto mislukt: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
                }
            });

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_defect);

        inspectieId = getIntent().getIntExtra(EXTRA_INSPECTIE_ID, -1);
        String titel = getIntent().getStringExtra(EXTRA_TITEL);

        TextView txtFixture = findViewById(R.id.txtFixture);
        txtFixture.setText(titel != null ? titel : ("ID " + inspectieId));

        sp = findViewById(R.id.spOmschrijving);
        img = findViewById(R.id.imgFoto);
        Button btnCamera = findViewById(R.id.btnCamera);
        Button btnGalerij = findViewById(R.id.btnGalerij);
        Button btnOpslaan = findViewById(R.id.btnOpslaan);

        ArrayList<String> keuzes = new ArrayList<>();
        keuzes.add("Kapotte lamp"); keuzes.add("Geen pictogram"); keuzes.add("Batterij defect");
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, keuzes);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);

        if (savedInstanceState != null) {
            String p = savedInstanceState.getString(STATE_LAST_PHOTO_PATH, null);
            if (p != null) { File f = new File(p); if (f.exists()) { lastPhotoFile = f; showPhoto(f); } }
        }

        btnCamera.setOnClickListener(v -> {
            lastPhotoFile = getNewPhotoFile();
            lastPhotoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", lastPhotoFile);
            takePhoto.launch(lastPhotoUri);
        });

        btnGalerij.setOnClickListener(v -> pickImage.launch("image/*"));
        btnOpslaan.setOnClickListener(v -> saveDefect());
    }

    @Override public void onBackPressed() { finish(); }

    @Override protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (lastPhotoFile != null) outState.putString(STATE_LAST_PHOTO_PATH, lastPhotoFile.getAbsolutePath());
    }

    private File getNewPhotoFile() {
        File dir = new File(getExternalFilesDir(null), "export/photos"); if (!dir.exists()) dir.mkdirs();
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return new File(dir, "gebrek_" + inspectieId + "_" + ts + ".jpg");
    }

    private void showPhoto(File f) {
        try (FileInputStream fis2 = new FileInputStream(f)) {
            BitmapFactory.Options opts = new BitmapFactory.Options(); opts.inSampleSize = 2;
            Bitmap bmp = BitmapFactory.decodeStream(fis2, null, opts);
            if (bmp != null) img.setImageBitmap(bmp); else img.setImageURI(Uri.fromFile(f));
        } catch (Exception e) { Toast.makeText(this, "Weergave mislukt: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void saveDefect() {
        String oms = (String) sp.getSelectedItem();
        String dt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String foto = lastPhotoFile != null ? lastPhotoFile.getAbsolutePath() : null;
        try {
            ContentValues cv = new ContentValues();
            cv.put("inspectie_id", inspectieId); cv.put("omschrijving", oms); cv.put("datum", dt);
            if (foto != null) cv.put("foto_pad", foto);
            long rowId = DBField.getInstance(this).getWritableDatabase().insert("gebreken", null, cv);
            Toast.makeText(this, rowId > 0 ? "Gebrek opgeslagen" : "Opslaan mislukt", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK); // default back stack naar FixtureListActivity
            finish();
        } catch (Exception e) { Toast.makeText(this, "Opslaan mislukt: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }
}
