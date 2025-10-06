package nl.mikekemmink.noodverlichting.noodverlichting.columns;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;

import nl.mikekemmink.noodverlichting.R;   // <-- let op: JOUW R, niet android.R!

public class ColumnOrderAdapter extends RecyclerView.Adapter<ColumnOrderAdapter.VH> {

    private final List<ColumnConfig> data;

    public ColumnOrderAdapter(List<ColumnConfig> data) {
        this.data = data;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return data.get(position).alias.hashCode();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_column_item, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ColumnConfig c = data.get(position);

        h.label.setText(c.label);
        h.check.setOnCheckedChangeListener(null);
        h.check.setChecked(c.visible);
        h.check.setOnCheckedChangeListener((btn, checked) -> c.visible = checked);
        // Geen handle: drag start gebeurt via long-press (zie ItemTouchHelper)
    }

    @Override
    public int getItemCount() { return data.size(); }

    public void moveItem(int from, int to) {
        if (from == to) return;
        Collections.swap(data, from, to);
        notifyItemMoved(from, to);
    }

    static class VH extends RecyclerView.ViewHolder {
        final CheckBox check;
        final TextView label;
        VH(@NonNull View v) {
            super(v);
            check  = v.findViewById(R.id.cbVisible);
            label  = v.findViewById(R.id.tvLabel);
        }
    }

    public static ItemTouchHelper.Callback makeTouchHelperCallback(ColumnOrderAdapter adapter) {
        return new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder target) {
                adapter.moveItem(vh.getAdapterPosition(), target.getAdapterPosition());
                return true;
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) { /* no-op */ }
            @Override public boolean isLongPressDragEnabled() { return true; } // <--- long-press om te slepen
        };
    }
    public List<ColumnConfig> getItems() {
        return data;
    }
}