package nl.mikekemmink.noodverlichting.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import nl.mikekemmink.noodverlichting.R;

/**
 * Generic locations adapter using unique IDs (String id + String title).
 * Supports list/grid by passing two layout resources that both expose @id/txtTitle
 * and use MaterialCardView as root (or give the root card an id and look it up).
 */
public class LocationsAdapterIds extends RecyclerView.Adapter<LocationsAdapterIds.VH> {

    public static final int TYPE_LIST = 0;
    public static final int TYPE_GRID = 1;

    public static class Item {
        public final String id;
        public final String title;
        public Item(String id, String title) {
            this.id = id;
            this.title = title;
        }
    }

    public interface OnItemClick { void onClick(String id, String title); }
    public interface OnItemLongClick { void onLongClick(String id, String title); }

    private final List<Item> items = new ArrayList<>();
    private final OnItemClick onItemClick;
    private OnItemLongClick onItemLongClick;

    private boolean isGrid;
    private final @LayoutRes int listLayoutRes;
    private final @LayoutRes int gridLayoutRes;

    public LocationsAdapterIds(List<Item> initialItems,
                               boolean isGrid,
                               OnItemClick onItemClick,
                               @LayoutRes int listLayoutRes,
                               @LayoutRes int gridLayoutRes) {
        if (initialItems != null) items.addAll(initialItems);
        this.isGrid = isGrid;
        this.onItemClick = onItemClick;
        this.listLayoutRes = listLayoutRes;
        this.gridLayoutRes = gridLayoutRes;
        setHasStableIds(true);
    }

    public void setOnItemLongClick(OnItemLongClick handler) { this.onItemLongClick = handler; }

    public void setGrid(boolean grid) {
        if (this.isGrid != grid) {
            this.isGrid = grid;
            notifyDataSetChanged();
        }
    }

    public void setItems(List<Item> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @Override public int getItemCount() { return items.size(); }
    @Override public int getItemViewType(int position) { return isGrid ? TYPE_GRID : TYPE_LIST; }

    // stable id gebaseerd op String id
    @Override public long getItemId(int position) { return stableId(items.get(position).id); }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = (viewType == TYPE_GRID) ? gridLayoutRes : listLayoutRes;
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Item it = items.get(position);
        h.title.setText(it.title);
        h.card.setOnClickListener(v -> onItemClick.onClick(it.id, it.title));
        if (onItemLongClick != null) {
            h.card.setOnLongClickListener(v -> { onItemLongClick.onLongClick(it.id, it.title); return true; });
        } else {
            h.card.setOnLongClickListener(null);
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView title;
        VH(@NonNull View itemView) {
            super(itemView);
            // root is a MaterialCardView in your layouts:
            card  = (MaterialCardView) itemView;
            title = itemView.findViewById(R.id.txtTitle);
        }
    }

    // Maak van String een stabiele long (RecyclerView requirement)
    private static long stableId(String s) {
        if (s == null) return 0L;
        // 32-bit -> 64-bit, stabiel over sessies
        return (long) Objects.hashCode(s) & 0xffffffffL;
    }
}