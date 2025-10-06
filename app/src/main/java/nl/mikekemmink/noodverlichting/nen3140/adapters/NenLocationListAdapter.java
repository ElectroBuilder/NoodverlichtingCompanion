package nl.mikekemmink.noodverlichting.nen3140.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.nen3140.NenLocation;

public class NenLocationListAdapter extends RecyclerView.Adapter<NenLocationListAdapter.VH> {
    public interface OnItemClick { void onClick(NenLocation item); }
    public interface OnItemLongClick { void onLongClick(NenLocation item); }

    private final OnItemClick onClick;
    private final OnItemLongClick onLongClick;
    private final List<NenLocation> items = new ArrayList<>();

    public NenLocationListAdapter(List<NenLocation> data, OnItemClick onClick, OnItemLongClick onLongClick) {
        if (data != null) items.addAll(data);
        this.onClick = onClick;
        this.onLongClick = onLongClick;
    }

    public void setItems(List<NenLocation> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_nen_simple, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final NenLocation item = items.get(position);
        h.title.setText(item.getName());
        h.itemView.setOnClickListener(v -> { if (onClick != null) onClick.onClick(item); });
        h.itemView.setOnLongClickListener(v -> { if (onLongClick != null) onLongClick.onLongClick(item); return true; });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView title;
        VH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.txtTitle);
        }
    }
}
