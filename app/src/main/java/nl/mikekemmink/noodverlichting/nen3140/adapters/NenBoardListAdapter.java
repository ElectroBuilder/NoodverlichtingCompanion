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
import nl.mikekemmink.noodverlichting.nen3140.NenBoard;

public class NenBoardListAdapter extends RecyclerView.Adapter<NenBoardListAdapter.VH> {
    public interface OnItemClick { void onClick(NenBoard item); }
    public interface OnItemLongClick { void onLongClick(NenBoard item); }

    private final OnItemClick onClick;
    private final OnItemLongClick onLongClick;
    private final List<NenBoard> items = new ArrayList<>();

    public NenBoardListAdapter(List<NenBoard> data, OnItemClick onClick, OnItemLongClick onLongClick) {
        if (data != null) items.addAll(data);
        this.onClick = onClick;
        this.onLongClick = onLongClick;
    }

    public void setItems(List<NenBoard> data) {
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
        final NenBoard item = items.get(position);
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
