package nl.mikekemmink.noodverlichting.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.List;

import nl.mikekemmink.noodverlichting.R;

public class LocationsAdapter extends RecyclerView.Adapter<LocationsAdapter.VH> {

    private static final int TYPE_LIST = 0;
    private static final int TYPE_GRID = 1;

    private final List<String> items;
    private final OnItemClick onItemClick;
    private boolean isGrid;

    private final @LayoutRes int listLayoutRes;
    private final @LayoutRes int gridLayoutRes;

    public interface OnItemClick {
        void onClick(String locationName);
    }


// Bestaande NV-constructie (defaults naar NV layouts)
  public LocationsAdapter(List<String> items, boolean isGrid, OnItemClick onItemClick) {
    this(items, isGrid, onItemClick, R.layout.nv_locaties_lijst, R.layout.nv_locaties_grid);
  }

  // Generieke constructie (voor NEN layout-ids, of andere)
  public LocationsAdapter(List<String> items, boolean isGrid, OnItemClick onItemClick,
                          @LayoutRes int listLayoutRes, @LayoutRes int gridLayoutRes) {
    this.items = items;
    this.isGrid = isGrid;
    this.onItemClick = onItemClick;
    this.listLayoutRes = listLayoutRes;
    this.gridLayoutRes = gridLayoutRes;
    setHasStableIds(true);
  }

  public void setGrid(boolean grid) {
    if (this.isGrid != grid) {
      this.isGrid = grid;
      notifyDataSetChanged();
    }
  }

    @Override public long getItemId(int position) { return items.get(position).hashCode(); }
    @Override public int getItemViewType(int position) { return isGrid ? TYPE_GRID : TYPE_LIST; }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = (viewType == TYPE_GRID) ? gridLayoutRes : listLayoutRes;
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        String name = items.get(position);
        h.title.setText(name);
        h.card.setOnClickListener(v -> onItemClick.onClick(name));
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView title;
        VH(@NonNull View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            title = itemView.findViewById(R.id.txtTitle);
        }
    }
}