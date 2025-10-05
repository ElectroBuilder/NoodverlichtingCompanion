
package nl.mikekemmink.noodverlichting.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import nl.mikekemmink.noodverlichting.R;

public class DefectAdapter extends RecyclerView.Adapter<DefectAdapter.VH> {

    public static class Item {
        public long id;
        public String omschrijving;
        public String datum; // yyyy-MM-dd
        public String fotoPad; // absolute path or null
    }

    private final List<Item> items = new ArrayList<>();

    public void submit(List<Item> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_defect, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Item it = items.get(pos);
        h.tvOmschrijving.setText(it.omschrijving != null ? it.omschrijving : "-");
        h.tvDatum.setText(it.datum != null ? it.datum : "");
        if (it.fotoPad != null) {
            File f = new File(it.fotoPad);
            if (f.exists()) {
                try (FileInputStream fis = new FileInputStream(f)) {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = 4; // klein previewtje
                    Bitmap bmp = BitmapFactory.decodeStream(fis, null, opts);
                    if (bmp != null) h.img.setImageBitmap(bmp);
                    else h.img.setImageURI(Uri.fromFile(f));
                } catch (Exception ignore) { h.img.setImageURI(Uri.fromFile(f)); }
            } else {
                h.img.setImageResource(android.R.drawable.ic_menu_report_image);
            }
        } else {
            h.img.setImageResource(android.R.drawable.ic_menu_report_image);
        }
    }
    public Item getItemAt(int position) {
        return items.get(position);
    }
    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvOmschrijving, tvDatum; ImageView img;
        VH(@NonNull View v) {
            super(v);
            tvOmschrijving = v.findViewById(R.id.tvOmschrijving);
            tvDatum = v.findViewById(R.id.tvDatum);
            img = v.findViewById(R.id.imgThumb);
        }
    }
}
