package nl.mikekemmink.noodverlichting.nen3140.adapters;

import android.content.ClipData;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.nen3140.NenBoard;
import nl.mikekemmink.noodverlichting.nen3140.NenStorage;

public class NenBoardListAdapter extends RecyclerView.Adapter<NenBoardListAdapter.VH> {
    public interface OnItemClick { void onClick(NenBoard board); }
    public interface OnItemLongClick { void onLongClick(NenBoard board); }

    private final String locationId;
    private final NenStorage storage;
    private List<NenBoard> items = new ArrayList<>();
    private final OnItemClick click;
    private final OnItemLongClick longClick;

    public NenBoardListAdapter(List<NenBoard> items, String locationId, NenStorage storage, OnItemClick click, OnItemLongClick longClick) {
        if (items != null) this.items = items; else this.items = new ArrayList<>();
        this.locationId = locationId;
        this.storage = storage;
        this.click = click;
        this.longClick = longClick;
    }

    public void setItems(List<NenBoard> newItems){
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.nen_verdelers, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        NenBoard b = items.get(position);
        h.title.setText(b.getName());

        boolean hasPhotos = storage.hasBoardPhotos(locationId, b.getId());
        boolean hasSpd = storage.hasSpdValues(locationId, b.getId());
        h.badgePhotos.setVisibility(hasPhotos ? View.VISIBLE : View.GONE);
        h.badgeSpd.setVisibility(hasSpd ? View.VISIBLE : View.GONE);

        h.itemView.setOnClickListener(v -> click.onClick(b));
        h.itemView.setOnLongClickListener(v -> { longClick.onLongClick(b); return true; });

        // Drag starten met drag-handle
        h.drag.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                ClipData data = ClipData.newPlainText("viId", b.getId());
                View.DragShadowBuilder shadow = new View.DragShadowBuilder(h.itemView);
                v.startDragAndDrop(data, shadow, null, 0);
                return true;
            }
            return false;
        });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView title, badgePhotos, badgeSpd; ImageView drag;
        VH(@NonNull View itemView){
            super(itemView);
            title = itemView.findViewById(R.id.tvTitle);
            badgePhotos = itemView.findViewById(R.id.tvBadgePhotos);
            badgeSpd = itemView.findViewById(R.id.tvBadgeSpd);
            drag = itemView.findViewById(R.id.ivDrag);
        }
    }
}