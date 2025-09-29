package nl.mikekemmink.noodverlichting.ui.gebreken;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import nl.mikekemmink.noodverlichting.R;
import java.util.List;

public class GebrekenAdapter extends RecyclerView.Adapter<GebrekenAdapter.VH> {

    public interface OnEditClick { void onEdit(Gebrek g, int position); }

    private final List<Gebrek> items;
    private final OnEditClick onEdit;

    public GebrekenAdapter(List<Gebrek> items, OnEditClick onEdit) {
        this.items = items;
        this.onEdit = onEdit;
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_gebrek, parent, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        Gebrek g = items.get(pos);
        h.tvOmschrijving.setText(g.omschrijving);
        h.tvDatum.setText(g.datum);
        h.itemView.setOnClickListener(v -> onEdit.onEdit(g, h.getAdapterPosition()));
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvOmschrijving, tvDatum;
        VH(@NonNull View itemView) {
            super(itemView);
            tvOmschrijving = itemView.findViewById(R.id.tvOmschrijving);
            tvDatum = itemView.findViewById(R.id.tvDatum);
        }
    }
}
