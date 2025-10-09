
package nl.mikekemmink.noodverlichting.nen3140.adapters;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.nen3140.NenDefect;

public class NenDefectListAdapter extends RecyclerView.Adapter<NenDefectListAdapter.VH> {
    public interface OnEdit {
        void onEdit(NenDefect d);
    }

    public interface OnDelete {
        void onDelete(NenDefect d);
    }

    public interface OnCamera {
        void onCamera(NenDefect d);
    }

    private final OnEdit onEdit;
    private final OnDelete onDelete;
    private final OnCamera onCamera;
    private List<NenDefect> items = new ArrayList<>();
    private final File photosDir;

    public NenDefectListAdapter(List<NenDefect> data, OnEdit onEdit, OnDelete onDelete, OnCamera onCamera) {
        if (data != null) this.items.addAll(data);
        this.onEdit = onEdit;
        this.onDelete = onDelete;
        this.onCamera = onCamera;
        this.photosDir = null; // set later via setter if needed
    }

    public void setItems(List<NenDefect> data) {
        this.items = (data != null) ? new ArrayList<>(data) : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_defect, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final NenDefect d = items.get(position);

        h.txtTitle.setText(d.text == null || d.text.isEmpty() ? "(geen omschrijving)" : d.text);
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd-MM-yyyy HH:mm", java.util.Locale.getDefault());
        h.txtDate.setText(sdf.format(new java.util.Date(d.timestamp)));

        if (d.photo != null && !d.photo.isEmpty()) {
            File f = new File(h.itemView.getContext().getFilesDir(), "nen3140/photos/" + d.photo);
            if (f.exists()) {
                int targetPx = dp(h.itemView.getContext(), 72);
                Bitmap bmp = decodeThumbWithRotation(f, targetPx, targetPx);
                h.image.setImageBitmap(bmp);
                h.image.setVisibility(View.VISIBLE);

                // Klik op de thumbnail -> fullscreen
                h.image.setOnClickListener(v -> openPhotoPreview(v.getContext(), d.photo));
            } else {
                h.image.setImageDrawable(null);
                h.image.setVisibility(View.GONE);
                h.image.setOnClickListener(null);
            }
        } else {
            h.image.setImageDrawable(null);
            h.image.setVisibility(View.GONE);
            h.image.setOnClickListener(null);
        }

        h.btnCamera.setOnClickListener(v -> {
            if (onCamera != null) onCamera.onCamera(d);
        });
        h.btnDelete.setOnClickListener(v -> {
            if (onDelete != null) onDelete.onDelete(d);
        });
        h.itemView.setOnClickListener(v -> {
            if (onEdit != null) onEdit.onEdit(d);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView image;
        TextView txtTitle;
        TextView txtDate;
        ImageButton btnCamera;
        ImageButton btnDelete;

        VH(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.imgPhoto);
            txtTitle = itemView.findViewById(R.id.txtTitle);
            txtDate = itemView.findViewById(R.id.txtDate);
            btnCamera = itemView.findViewById(R.id.btnCamera);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }

    private static int dp(android.content.Context c, int dp) {
        return Math.round(dp * c.getResources().getDisplayMetrics().density);
    }

    public static Bitmap decodeThumbWithRotation(File file, int reqW, int reqH) {
        // 1) bounds lezen
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), o);

        // 2) sample-size bepalen
        o.inSampleSize = calcInSampleSize(o, reqW, reqH);
        o.inJustDecodeBounds = false;
        o.inPreferredConfig = Bitmap.Config.RGB_565; // zuiniger

        // 3) decoden
        Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath(), o);
        if (bmp == null) return null;

        // 4) EXIF-rotatie toepassen
        try {
            androidx.exifinterface.media.ExifInterface exif =
                    new androidx.exifinterface.media.ExifInterface(file.getAbsolutePath());
            int orientation = exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL);
            android.graphics.Matrix m = new android.graphics.Matrix();
            switch (orientation) {
                case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90:
                    m.postRotate(90);
                    break;
                case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180:
                    m.postRotate(180);
                    break;
                case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270:
                    m.postRotate(270);
                    break;
                default:
                    return bmp;
            }
            Bitmap rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            bmp.recycle();
            return rotated;
        } catch (Exception ignore) {
            return bmp;
        }
    }

    private static int calcInSampleSize(BitmapFactory.Options o, int reqW, int reqH) {
        int h = o.outHeight, w = o.outWidth;
        int inSample = 1;
        if (h > reqH || w > reqW) {
            int halfH = h / 2, halfW = w / 2;
            while ((halfH / inSample) >= reqH && (halfW / inSample) >= reqW) {
                inSample *= 2;
            }
        }
        return inSample;
    }

    private void openPhotoPreview(android.content.Context ctx, String fileName) {
        android.content.Intent i = new android.content.Intent(ctx, nl.mikekemmink.noodverlichting.nen3140.PhotoPreviewActivity.class);
        i.putExtra("photo", fileName);
        ctx.startActivity(i);

    }
}