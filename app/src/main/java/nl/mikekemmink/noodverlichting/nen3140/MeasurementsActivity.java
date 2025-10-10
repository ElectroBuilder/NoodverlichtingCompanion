package nl.mikekemmink.noodverlichting.nen3140;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.Date;
import androidx.exifinterface.media.ExifInterface;
import android.util.TypedValue;
import android.graphics.Matrix;
import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.ui.BaseToolbarActivity;

public class MeasurementsActivity extends BaseToolbarActivity {
  private EditText etL1, etL2, etL3, etN, etPE;
  private EditText etSpdL1, etSpdL2, etSpdL3, etSpdN;
  private ImageView imgBoard, imgInfo;
  private View phPhotoBoard, phPhotoInfo;
  private Uri uriBoard, uriInfo;
  private File fileBoard, fileInfo;
  private ActivityResultLauncher<Uri> takeBoard;
  private ActivityResultLauncher<Uri> takeInfo;

  private String scope;
  private String locationId;
  private String boardId;
  private String nenMeasurementId;

  private ActivityResultLauncher<Uri> takeDefect;
  private String pendingDefectId, pendingDefectFileName;
  private Uri pendingDefectUri;

  private String boardPhotoPath, infoPhotoPath;

  private static final int REQ_PICK_BOARD = 401;
  private static final int REQ_PICK_INFO = 402;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentLayout(R.layout.nen_metingen);
    applyPalette(Palette.NEN);
    setUpEnabled(true);
    if (getSupportActionBar() != null) {
      getSupportActionBar().setTitle(R.string.title_measurements);
    }

    etL1 = findViewById(R.id.et_l1);
    etL2 = findViewById(R.id.et_l2);
    etL3 = findViewById(R.id.et_l3);
    etN  = findViewById(R.id.et_n);
    etPE = findViewById(R.id.et_pe);

    etSpdL1 = findViewById(R.id.et_spd_l1);
    etSpdL2 = findViewById(R.id.et_spd_l2);
    etSpdL3 = findViewById(R.id.et_spd_l3);
    etSpdN  = findViewById(R.id.et_spd_N);

    imgBoard = findViewById(R.id.img_board);
    imgInfo  = findViewById(R.id.img_info);
    phPhotoBoard = findViewById(R.id.phPhotoBoard);
    phPhotoInfo  = findViewById(R.id.phPhotoInfo);

    scope       = getIntent().getStringExtra("scope");
    locationId  = getIntent().getStringExtra("locationId");
    boardId     = getIntent().getStringExtra("boardId");
    nenMeasurementId = getIntent().getStringExtra("nenMeasurementId");
    String prefillKast = getIntent().getStringExtra("prefillKastnaam");
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

    if ("NEN".equals(scope)) {
      NenStorage nen = new NenStorage(this);
      NenBoard board = nen.getBoard(locationId, boardId);
      if (board != null) {
        boardPhotoPath = board.getPhotoBoardPath();
        infoPhotoPath  = board.getPhotoInfoPath();
      }
      bindCurrentPhotos();

      NenMeasurement em = null;
      if (nenMeasurementId != null) {
        em = nen.getMeasurement(locationId, boardId, nenMeasurementId);
      }
      if (em == null) {
        em = nen.getLastMeasurement(locationId, boardId);
      }
      if (em != null) {
        if (em.L1 != null) etL1.setText(String.valueOf(em.L1));
        if (em.L2 != null) etL2.setText(String.valueOf(em.L2));
        if (em.L3 != null) etL3.setText(String.valueOf(em.L3));
        if (em.N  != null) etN.setText(String.valueOf(em.N));
        if (em.PE != null) etPE.setText(String.valueOf(em.PE));
        if (em.spdL1 != null) etSpdL1.setText(String.valueOf(em.spdL1));
        if (em.spdL2 != null) etSpdL2.setText(String.valueOf(em.spdL2));
        if (em.spdL3 != null) etSpdL3.setText(String.valueOf(em.spdL3));
        if (em.spdN  != null) etSpdN.setText(String.valueOf(em.spdN));
      }
    }

    takeBoard = registerForActivityResult(
      new ActivityResultContracts.TakePicture(),
      success -> {
        if (success && uriBoard != null && fileBoard != null && fileBoard.exists()) {
          NenStorage nen = new NenStorage(this);
          nen.updateBoardPhotos(locationId, boardId, fileBoard.getAbsolutePath(), null);
          boardPhotoPath = fileBoard.getAbsolutePath();
          bindCurrentPhotos();
        }
      }
    );
    takeInfo = registerForActivityResult(
      new ActivityResultContracts.TakePicture(),
      success -> {
        if (success && uriInfo != null && fileInfo != null && fileInfo.exists()) {
          NenStorage nen = new NenStorage(this);
          nen.updateBoardPhotos(locationId, boardId, null, fileInfo.getAbsolutePath());
          infoPhotoPath = fileInfo.getAbsolutePath();
          bindCurrentPhotos();
        }
      }
    );

    if (phPhotoBoard != null) phPhotoBoard.setOnClickListener(v -> openCameraFor(false));
    if (phPhotoInfo  != null) phPhotoInfo.setOnClickListener(v -> openCameraFor(true));
    attachGestures(imgBoard, false);
    attachGestures(imgInfo, true);

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

    Button btnCancel = findViewById(R.id.btn_cancel);
    Button btnSave   = findViewById(R.id.btn_save);
    btnCancel.setOnClickListener(v -> finish());
    btnSave.setOnClickListener(v -> onSave());
  }

  private void attachGestures(ImageView iv, boolean isInfo) {
    if (iv == null) return;
    iv.setOnClickListener(v -> {
      String path = isInfo ? infoPhotoPath : boardPhotoPath;
      if (path != null && !path.isEmpty()) openFullScreen(path);
      else openCameraFor(isInfo);
    });
    GestureDetector detector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
      @Override public boolean onSingleTapConfirmed(MotionEvent e) {
        String path = isInfo ? infoPhotoPath : boardPhotoPath;
        if (path != null && !path.isEmpty()) openFullScreen(path);
        else openCameraFor(isInfo);
        return true;
      }
      @Override public boolean onDoubleTap(MotionEvent e) {
        openCameraFor(isInfo);
        return true;
      }
    });
    iv.setOnTouchListener((v, event) -> { detector.onTouchEvent(event); return true; });
  }

  private void openFullScreen(String path) {
    try {
      Intent i = new Intent(this, PhotoPreviewActivity.class);
      i.putExtra("photoPath", path);
      startActivity(i);
    } catch (Exception ex) {
      try {
        Uri u = Uri.fromFile(new File(path));
        Intent view = new Intent(Intent.ACTION_VIEW);
        view.setDataAndType(u, "image/*");
        view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(view);
      } catch (Exception ignore) {
        Toast.makeText(this, "Kan foto niet openen", Toast.LENGTH_LONG).show();
      }
    }
  }

  private void openCameraFor(boolean isInfo) {
    try {
      File dir = new File(getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "nen3140");
      if (!dir.exists()) dir.mkdirs();
      String time = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
      if (isInfo) {
        fileInfo = new File(dir, "info_" + time + ".jpg");
        uriInfo = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", fileInfo);
        takeInfo.launch(uriInfo);
      } else {
        fileBoard = new File(dir, "board_" + time + ".jpg");
        uriBoard = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", fileBoard);
        takeBoard.launch(uriBoard);
      }
    } catch (Exception e) {
      e.printStackTrace();
      Toast.makeText(this, "Camera openen mislukt: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }
  }

  private void bindCurrentPhotos() {
    bindThumb(imgBoard, phPhotoBoard, boardPhotoPath);
    bindThumb(imgInfo,  phPhotoInfo,  infoPhotoPath);
  }

  private void bindThumb(ImageView iv, View placeholder, String path) {
    if (iv == null || placeholder == null) return;
    if (path != null && !path.isEmpty()) {
      iv.post(() -> {
        int w = iv.getWidth();
        int h = iv.getHeight();
        if (w <= 0 || h <= 0) {
          int px = dpToPx(120);
          w = px; h = px;
        }
        Bitmap bm = decodeThumbWithExif(path, w, h);
        if (bm != null) {
          iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
          iv.setImageBitmap(bm);
          iv.setVisibility(View.VISIBLE);
          placeholder.setVisibility(View.GONE);
        } else {
          iv.setImageDrawable(null);
          iv.setVisibility(View.GONE);
          placeholder.setVisibility(View.VISIBLE);
        }
      });
    } else {
      iv.setImageDrawable(null);
      iv.setVisibility(View.GONE);
      placeholder.setVisibility(View.VISIBLE);
    }
  }

  private int dpToPx(int dp) {
    return Math.round(TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()));
  }

  private Bitmap decodeThumbWithExif(String filePath, int reqWpx, int reqHpx) {
    try {
      BitmapFactory.Options o = new BitmapFactory.Options();
      o.inJustDecodeBounds = true;
      BitmapFactory.decodeFile(filePath, o);
      int inSample = 1;
      int halfW = o.outWidth;
      int halfH = o.outHeight;
      while ((halfW / inSample) > reqWpx || (halfH / inSample) > reqHpx) {
        inSample *= 2;
      }
      BitmapFactory.Options o2 = new BitmapFactory.Options();
      o2.inSampleSize = Math.max(1, inSample);
      o2.inPreferredConfig = Bitmap.Config.ARGB_8888;
      Bitmap bm = BitmapFactory.decodeFile(filePath, o2);
      if (bm == null) return null;
      bm = applyExifOrientation(filePath, bm);
      if (bm.getWidth() > reqWpx || bm.getHeight() > reqHpx) {
        float ratio = Math.min(reqWpx / (float) bm.getWidth(), reqHpx / (float) bm.getHeight());
        bm = Bitmap.createScaledBitmap(bm,
          Math.round(bm.getWidth() * ratio),
          Math.round(bm.getHeight() * ratio),
          true);
      }
      return bm;
    } catch (Exception e) {
      return null;
    }
  }

  private Bitmap applyExifOrientation(String filePath, Bitmap source) {
    try {
      ExifInterface exif = new ExifInterface(filePath);
      int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
      Matrix m = new Matrix();
      switch (orientation) {
        case ExifInterface.ORIENTATION_ROTATE_90: m.postRotate(90); break;
        case ExifInterface.ORIENTATION_ROTATE_180: m.postRotate(180); break;
        case ExifInterface.ORIENTATION_ROTATE_270: m.postRotate(270); break;
        case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: m.preScale(-1, 1); break;
        case ExifInterface.ORIENTATION_FLIP_VERTICAL: m.preScale(1, -1); break;
        case ExifInterface.ORIENTATION_TRANSPOSE: m.postRotate(90); m.preScale(-1, 1); break;
        case ExifInterface.ORIENTATION_TRANSVERSE: m.postRotate(270); m.preScale(-1, 1); break;
        default: return source;
      }
      return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), m, true);
    } catch (Exception ignored) {
      return source;
    }
  }

  private void onSave() {
    Double l1D = parseNullableDouble(etL1.getText().toString());
    Double l2D = parseNullableDouble(etL2.getText().toString());
    Double l3D = parseNullableDouble(etL3.getText().toString());
    Double nD  = parseNullableDouble(etN.getText().toString());
    Double peD = parseNullableDouble(etPE.getText().toString());
    Double spdL1 = parseNullableDouble(etSpdL1.getText().toString());
    Double spdL2 = parseNullableDouble(etSpdL2.getText().toString());
    Double spdL3 = parseNullableDouble(etSpdL3.getText().toString());
    Double spdN  = parseNullableDouble(etSpdN.getText().toString());
    long ts = System.currentTimeMillis();

    if ("NEN".equals(scope)) {
      NenStorage nen = new NenStorage(this);

      String bPath = (fileBoard != null && fileBoard.exists()) ? fileBoard.getAbsolutePath() : null;
      String iPath = (fileInfo  != null && fileInfo.exists())  ? fileInfo.getAbsolutePath()  : null;
      if (bPath != null || iPath != null) {
        nen.updateBoardPhotos(locationId, boardId, bPath, iPath);
      }

      String id = UUID.randomUUID().toString();
      NenMeasurement nm = new NenMeasurement(id, l1D, l2D, l3D, nD, peD, ts);
      nm.spdL1 = spdL1; nm.spdL2 = spdL2; nm.spdL3 = spdL3; nm.spdN = spdN;

      // >>> NIEUW: relatievelden zetten
      nm.locationId = locationId;
      nm.boardId    = boardId;

      nen.addMeasurement(locationId, boardId, nm);
      Toast.makeText(this, R.string.msg_measurement_saved, Toast.LENGTH_SHORT).show();
      finish();
      return;
    }
    Toast.makeText(this, getString(R.string.msg_no_nen_scope), Toast.LENGTH_SHORT).show();
    finish();
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
}
