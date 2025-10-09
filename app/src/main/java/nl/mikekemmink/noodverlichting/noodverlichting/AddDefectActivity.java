package nl.mikekemmink.noodverlichting.noodverlichting;

import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.noodverlichting.data.DBField;

public class AddDefectActivity extends AppCompatActivity {
    private long defectId = -1;
    public static final String EXTRA_DEFECT_ID = "defect_id";
    public static final String EXTRA_INSPECTIE_ID = "inspectie_id";
    public static final String EXTRA_TITEL = "titel";
    private static final String STATE_LAST_PHOTO_PATH = "state_last_photo_path";

    private int inspectieid;
    private Spinner sp;
    private ImageView img;
    private Uri lastPhotoUri;
    private File lastPhotoFile;

    private final ActivityResultLauncher<Uri> takePhoto =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
                if (ok != null && ok) {
                    showPhoto(lastPhotoFile);
                } else {
                    lastPhotoFile = null;
                    lastPhotoUri = null;
                }
            });

    private final ActivityResultLauncher<String> pickImage =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    try {
                        File dst = getNewPhotoFile();
                        try (InputStream in = getContentResolver().openInputStream(uri);
                             FileOutputStream out = new FileOutputStream(dst)) {
                            byte[] buf = new byte[8192];
                            int n;
                            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        }
                        lastPhotoFile = dst;
                        showPhoto(dst);
                    } catch (Exception e) {
                        Toast.makeText(this, "Import foto mislukt: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.nv_gebrek_toevoegen);

        // 1) Views eerst
        TextView txtFixture = findViewById(R.id.txtFixture);
        sp  = findViewById(R.id.spOmschrijving);
        img = findViewById(R.id.imgFoto);

        Button btnCamera  = findViewById(R.id.btnCamera);
        Button btnGalerij = findViewById(R.id.btnGalerij);
        Button btnOpslaan = findViewById(R.id.btnOpslaan);

        // 2) Spinner vullen
        ArrayList<String> keuzes = new ArrayList<>();
        keuzes.add("Kapotte lamp");
        keuzes.add("Geen pictogram");
        keuzes.add("Batterij defect");
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, keuzes);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);

        // 3) Herstel foto van rotations e.d.
        if (savedInstanceState != null) {
            String p = savedInstanceState.getString(STATE_LAST_PHOTO_PATH, null);
            if (p != null) {
                File f = new File(p);
                if (f.exists()) {
                    lastPhotoFile = f;
                    showPhoto(f);
                }
            }
        }

        // 4) Extras uitlezen (nádat views/spinner klaar zijn)
        defectId = getIntent().getLongExtra(EXTRA_DEFECT_ID, -1);
        String titel = getIntent().getStringExtra(EXTRA_TITEL);

        if (defectId > 0) {
            // Bewerken: laad record → zet inspectieid, spinnerkeuze, foto
            loadDefect();
            // Titel updaten nu we het inspectieid zeker weten
            txtFixture.setText(titel != null ? titel : ("ID " + inspectieid));
        } else {
            // Nieuw: haal inspectie_id uit intent
            inspectieid = getIntent().getIntExtra(EXTRA_INSPECTIE_ID, -1);
            txtFixture.setText(titel != null ? titel : ("ID " + inspectieid));
        }

        // 5) Listeners
        btnCamera.setOnClickListener(v -> {
            lastPhotoFile = getNewPhotoFile();
            lastPhotoUri  = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", lastPhotoFile);
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, 1001);
                return;
            }
            takePhoto.launch(lastPhotoUri);
        });

        btnGalerij.setOnClickListener(v -> pickImage.launch("image/*"));
        btnOpslaan.setOnClickListener(v -> saveDefect());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // PATCH: neem direct de foto als we al een target-URI klaar hadden
                if (lastPhotoUri != null) {
                    takePhoto.launch(lastPhotoUri);
                } else {
                    Toast.makeText(this, "Camera-toestemming verleend", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Camera-toestemming geweigerd", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (lastPhotoFile != null)
            outState.putString(STATE_LAST_PHOTO_PATH, lastPhotoFile.getAbsolutePath());
    }

    private File getNewPhotoFile() {
        File dir = new File(getExternalFilesDir(null), "export/photos");
        if (!dir.exists()) dir.mkdirs();
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return new File(dir, "gebrek_" + inspectieid + "_" + ts + ".jpg");
    }

    private void showPhoto(File f) {
        try (FileInputStream fis2 = new FileInputStream(f)) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 2;
            Bitmap bmp = BitmapFactory.decodeStream(fis2, null, opts);
            if (bmp != null) img.setImageBitmap(bmp);
            else img.setImageURI(Uri.fromFile(f));
        } catch (Exception e) {
            Toast.makeText(this, "Weergave mislukt: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void saveDefect() {
        // PATCH: valideer inspectieid
        if (inspectieid <= 0 && defectId <= 0) {
            Toast.makeText(this, "Geen geldig inspectie_id – kan niet opslaan.", Toast.LENGTH_LONG).show();
            return;
        }

        String oms = (String) sp.getSelectedItem();
        String dt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String foto = lastPhotoFile != null ? lastPhotoFile.getAbsolutePath() : null;

        try {
            ContentValues cv = new ContentValues();
            cv.put("inspectie_id", inspectieid);
            cv.put("omschrijving", oms);
            cv.put("datum", dt);
            if (foto != null) cv.put("foto_pad", foto);

            if (defectId > 0) {
                int rows = DBField.getInstance(this).getWritableDatabase().update(
                        "gebreken", cv, "id = ?", new String[]{String.valueOf(defectId)});

                if (rows > 0) {
                    Toast.makeText(this, "Gebrek bijgewerkt", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    // PATCH: kan ‘geen wijziging’ zijn – check of rij bestaat
                    boolean exists;
                    try (Cursor c = DBField.getInstance(this).getReadableDatabase().rawQuery(
                            "SELECT 1 FROM gebreken WHERE id = ? LIMIT 1",
                            new String[]{String.valueOf(defectId)})) {
                        exists = c.moveToFirst();
                    }
                    if (exists) {
                        Toast.makeText(this, "Geen wijzigingen", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    } else {
                        Toast.makeText(this, "Bijwerken mislukt (item niet gevonden)", Toast.LENGTH_LONG).show();
                    }
                }
            } else {
                long rowId = DBField.getInstance(this).getWritableDatabase()
                        .insert("gebreken", null, cv);
                if (rowId > 0) {
                    Toast.makeText(this, "Gebrek opgeslagen", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    Toast.makeText(this, "Opslaan mislukt", Toast.LENGTH_LONG).show();
                }
            }
        } catch (Exception e) {
            // Gooi niet hard naar boven; toon nette melding
            Toast.makeText(this, "Fout bij opslaan: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void loadDefect() {
        if (defectId <= 0) return;
        try (Cursor c = DBField.getInstance(this).getReadableDatabase().rawQuery(
                "SELECT inspectie_id, omschrijving, datum, foto_pad FROM gebreken WHERE id = ?",
                new String[]{ String.valueOf(defectId) })) {
            if (c.moveToFirst()) {
                inspectieid = c.getInt(0);
                String omschrijving = c.getString(1);
                String fotoPad = c.getString(3);

                // Selecteer juiste item in spinner
                ArrayAdapter<String> adapter = (ArrayAdapter<String>) sp.getAdapter();
                int pos = adapter.getPosition(omschrijving);
                if (pos >= 0) sp.setSelection(pos);

                // Toon foto als die er is
                if (fotoPad != null) {
                    File f = new File(fotoPad);
                    if (f.exists()) {
                        lastPhotoFile = f;
                        showPhoto(f);
                    }
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Laden mislukt: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
