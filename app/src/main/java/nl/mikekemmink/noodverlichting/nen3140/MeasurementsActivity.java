
package nl.mikekemmink.noodverlichting.nen3140;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.File;
import java.util.UUID;

import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.ui.BaseToolbarActivity;

public class MeasurementsActivity extends BaseToolbarActivity {

    // UI (kastnaamveld is uit de layout verwijderd)
    private EditText etL1, etL2, etL3, etN, etPE;

    // SPD UI
    private EditText etSpdL1, etSpdL2, etSpdL3, etSpdN;

    // Foto UI + state (board-level)
    private ImageView imgBoard, imgInfo;
    private Uri uriBoard, uriInfo;
    private File fileBoard, fileInfo;
    private ActivityResultLauncher<Uri> takeBoard;
    private ActivityResultLauncher<Uri> takeInfo;

    // Context (NEN)
    private String scope;       // "NEN"
    private String locationId;  // bij NEN
    private String boardId;     // bij NEN
    private String nenMeasurementId; // optioneel: specifieke meting bewerken

    // Defect-fotoflow (via toolbar-actie)
    private ActivityResultLauncher<Uri> takeDefect;
    private String pendingDefectId, pendingDefectFileName;
    private Uri pendingDefectUri;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.activity_measurements);
        applyPalette(Palette.NEN);
        setUpEnabled(true);

        // Standaardtitel (wordt zometeen overschreven door kastnaam)
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.title_measurements);
        }

        // bind views (kastnaam bestaat niet meer in layout)
        etL1 = findViewById(R.id.et_l1);
        etL2 = findViewById(R.id.et_l2);
        etL3 = findViewById(R.id.et_l3);
        etN  = findViewById(R.id.et_n);
        etPE = findViewById(R.id.et_pe);

        // SPD
        etSpdL1 = findViewById(R.id.et_spd_l1);
        etSpdL2 = findViewById(R.id.et_spd_l2);
        etSpdL3 = findViewById(R.id.et_spd_l3);
        etSpdN = findViewById(R.id.et_spd_N);

        // Foto
        imgBoard = findViewById(R.id.img_board);
        imgInfo  = findViewById(R.id.img_info);
        Button btnPhotoBoard = findViewById(R.id.btn_photo_board);
        Button btnPhotoInfo  = findViewById(R.id.btn_photo_info);

        // extras
        scope = getIntent().getStringExtra("scope");
        locationId = getIntent().getStringExtra("locationId");
        boardId = getIntent().getStringExtra("boardId");
        nenMeasurementId = getIntent().getStringExtra("nenMeasurementId");
        String prefillKast = getIntent().getStringExtra("prefillKastnaam");

        // Zet kastnaam in toolbar: eerst prefill, anders uit storage
        if (getSupportActionBar() != null && prefillKast != null && !prefillKast.isEmpty()) {
            getSupportActionBar().setTitle(prefillKast);
        } else if ("NEN".equals(scope)) {
            try {
                NenStorage nenForTitle = new NenStorage(this);
                NenBoard bForTitle = nenForTitle.getBoard(locationId, boardId);
                if (bForTitle != null && getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(bForTitle.getName());
                }
            } catch (Exception ignore) { }
        }

        // Prefill NEN: board-foto's en (laatste of specifieke) meting
        if ("NEN".equals(scope)) {
            NenStorage nen = new NenStorage(this);
            NenBoard board = nen.getBoard(locationId, boardId);
            if (board != null) {
                if (board.getPhotoBoardPath() != null) {
                    File f = new File(board.getPhotoBoardPath());
                    if (f.exists()) imgBoard.setImageURI(Uri.fromFile(f));
                }
                if (board.getPhotoInfoPath() != null) {
                    File f = new File(board.getPhotoInfoPath());
                    if (f.exists()) imgInfo.setImageURI(Uri.fromFile(f));
                }
            }
            NenMeasurement em = null;
            if (nenMeasurementId != null) {
                em = nen.getMeasurement(locationId, boardId, nenMeasurementId);
            }
            if (em == null) {
                em = nen.getLastMeasurement(locationId, boardId);
            }
            if (em != null) {
                // Alleen invullen als er echt een waarde is
                if (em.L1 != null) etL1.setText(String.valueOf(em.L1));
                if (em.L2 != null) etL2.setText(String.valueOf(em.L2));
                if (em.L3 != null) etL3.setText(String.valueOf(em.L3));
                if (em.N  != null) etN.setText(String.valueOf(em.N));
                if (em.PE != null) etPE.setText(String.valueOf(em.PE));

                if (em.spdL1 != null) etSpdL1.setText(String.valueOf(em.spdL1));
                if (em.spdL2 != null) etSpdL2.setText(String.valueOf(em.spdL2));
                if (em.spdL3 != null) etSpdL3.setText(String.valueOf(em.spdL3));
                if (em.spdN != null) etSpdN.setText(String.valueOf(em.spdN));
            }
        }

        // Activity Result: board/info foto's
        takeBoard = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> { if (success && uriBoard != null) imgBoard.setImageURI(uriBoard); }
        );
        takeInfo = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> { if (success && uriInfo != null) imgInfo.setImageURI(uriInfo); }
        );

        btnPhotoBoard.setOnClickListener(v -> {
            fileBoard = createImageFile("board_");
            uriBoard = FileProvider.getUriForFile(this, getPackageName()+".fileprovider", fileBoard);
            takeBoard.launch(uriBoard);
        });
        btnPhotoInfo.setOnClickListener(v -> {
            fileInfo = createImageFile("info_");
            uriInfo = FileProvider.getUriForFile(this, getPackageName()+".fileprovider", fileInfo);
            takeInfo.launch(uriInfo);
        });

        // Defect-fotoflow
        takeDefect = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (success && pendingDefectId != null && pendingDefectFileName != null) {
                        NenStorage nen = new NenStorage(this);
                        nen.setDefectPhoto(locationId, boardId, pendingDefectId, pendingDefectFileName);
                        Toast.makeText(this, getString(R.string.msg_defect_photo_saved), Toast.LENGTH_SHORT).show();
                    }
                    pendingDefectId = null;
                    pendingDefectFileName = null;
                    pendingDefectUri = null;
                });

        // Onderste knoppen (blijven bestaan)
        Button btnCancel = findViewById(R.id.btn_cancel);
        Button btnSave   = findViewById(R.id.btn_save);
        btnCancel.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> onSave());
    }

    private void onSave() {
        // Bewaar nullable waarden (niet naar 0.0 forceren)
        Double l1D = parseNullableDouble(etL1.getText().toString());
        Double l2D = parseNullableDouble(etL2.getText().toString());
        Double l3D = parseNullableDouble(etL3.getText().toString());
        Double nD  = parseNullableDouble(etN.getText().toString());
        Double peD = parseNullableDouble(etPE.getText().toString());

        // SPD
        Double spdL1 = parseNullableDouble(etSpdL1.getText().toString());
        Double spdL2 = parseNullableDouble(etSpdL2.getText().toString());
        Double spdL3 = parseNullableDouble(etSpdL3.getText().toString());
        Double spdN = parseNullableDouble(etSpdN.getText().toString());

        long ts = System.currentTimeMillis();

        if ("NEN".equals(scope)) {
            NenStorage nen = new NenStorage(this);

            // 1) Board/Info foto-paden wegschrijven als er iets nieuws is
            String boardPath = (fileBoard != null && fileBoard.exists()) ? fileBoard.getAbsolutePath() : null;
            String infoPath  = (fileInfo  != null && fileInfo.exists())  ? fileInfo.getAbsolutePath()  : null;
            if (boardPath != null || infoPath != null) {
                nen.updateBoardPhotos(locationId, boardId, boardPath, infoPath);
            }

            // 2) Meting opslaan (incl. SPD); lege velden blijven null en worden niet weggeschreven
            String id = UUID.randomUUID().toString();
            NenMeasurement nm = new NenMeasurement(id, l1D, l2D, l3D, nD, peD, ts);
            nm.spdL1 = spdL1; nm.spdL2 = spdL2; nm.spdL3 = spdL3; nm.spdN = spdN;
            nen.addMeasurement(locationId, boardId, nm);

            Toast.makeText(this, R.string.msg_measurement_saved, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Indien ooit zonder scope aangeroepen
        Toast.makeText(this, getString(R.string.msg_no_nen_scope), Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected int getActivityToolbarMenuRes() {
        return R.menu.menu_measurements; // menu met gebrek-acties
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_add_defect) {
            showAddDefectDialog();
            return true;
        } else if (id == R.id.action_manage_defects) {
            if (locationId != null && boardId != null) {
                Intent di = new Intent(this, DefectsActivity.class);
                di.putExtra("locationId", locationId);
                di.putExtra("boardId", boardId);
                startActivity(di);
            } else {
                Toast.makeText(this, getString(R.string.msg_board_unknown), Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAddDefectDialog() {
        if (locationId == null || boardId == null) {
            Toast.makeText(this, getString(R.string.msg_no_board_selected), Toast.LENGTH_LONG).show();
            return;
        }
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(getString(R.string.hint_defect_description));
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.dialog_add_defect_title)
                .setView(input)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String t = input.getText().toString().trim();
                    NenStorage nen = new NenStorage(this);
                    NenDefect created = nen.addDefect(locationId, boardId, t.isEmpty() ? null : t);
                    if (created != null) startCameraForDefect(created.id);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void startCameraForDefect(String defectId) {
        try {
            File photosDir = new NenStorage(this).getPhotosDir(); // .../files/nen3140/photos
            if (!photosDir.exists()) photosDir.mkdirs();
            String fileName = "defect_" + defectId + "_" + System.currentTimeMillis() + ".jpg";
            File out = new File(photosDir, fileName);
            pendingDefectId = defectId;
            pendingDefectFileName = fileName;
            pendingDefectUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", out);
            takeDefect.launch(pendingDefectUri);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.msg_camera_failed), Toast.LENGTH_LONG).show();
            pendingDefectId = null; pendingDefectFileName = null; pendingDefectUri = null;
        }
    }

    private @Nullable Double parseNullableDouble(String raw) {
        if (TextUtils.isEmpty(raw)) return null;
        try {
            String normalized = raw.replace(',', '.');
            return Double.parseDouble(normalized);
        } catch (NumberFormatException ex) {
            Toast.makeText(this, getString(R.string.error_invalid_number, raw), Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private File createImageFile(String prefix) {
        // Externe app-opslag: .../Android/data/<pkg>/files/Pictures/nen3140/
        File dir = new File(getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "nen3140");
        if (!dir.exists()) dir.mkdirs();
        String time = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                .format(new java.util.Date());
        return new File(dir, prefix + time + ".jpg");
    }
}
