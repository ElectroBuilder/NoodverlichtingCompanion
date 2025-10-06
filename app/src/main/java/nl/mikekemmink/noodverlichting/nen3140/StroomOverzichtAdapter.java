package nl.mikekemmink.noodverlichting.nen3140;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import nl.mikekemmink.noodverlichting.R;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StroomOverzichtAdapter extends RecyclerView.Adapter<StroomOverzichtAdapter.VH> {
    private final List<StroomWaardeEntry> data = new ArrayList<StroomWaardeEntry>();

    public interface OnItemClick { void onClick(StroomWaardeEntry e); }
    private OnItemClick onItemClick;
    public void setOnItemClick(OnItemClick l) { this.onItemClick = l; }

    public void submit(List<StroomWaardeEntry> list) {
        data.clear();
        data.addAll(list);
        notifyDataSetChanged();
    }

    public StroomWaardeEntry getItem(int pos) { return data.get(pos); }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_stroom_waarde, parent, false);
        final VH vh = new VH(v);
        v.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                int pos = vh.getAdapterPosition();
                if (onItemClick != null && pos != RecyclerView.NO_POSITION) onItemClick.onClick(getItem(pos));
            }
        });
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        StroomWaardeEntry e = data.get(position);
        h.title.setText(e.kastNaam);
        h.subtitle.setText(String.format(Locale.getDefault(),
                "L1 %.1f  L2 %.1f  L3 %.1f  N %.1f  PE %.1f",
                e.l1, e.l2, e.l3, e.n, e.pe));
        h.date.setText(DateFormat.getDateTimeInstance().format(new Date(e.timestamp)));
    }

    @Override
    public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView title, subtitle, date;
        VH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.txtTitle);
            subtitle = v.findViewById(R.id.txtSubtitle);
            date = v.findViewById(R.id.txtDate);
        }
    }
}
