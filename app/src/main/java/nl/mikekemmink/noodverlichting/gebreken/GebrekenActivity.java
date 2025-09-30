package nl.mikekemmink.noodverlichting.gebreken;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;
import nl.mikekemmink.noodverlichting.R;

public class GebrekenActivity extends AppCompatActivity {

    private RecyclerView recycler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gebreken);

        // Toolbar als app bar + terugknop
        MaterialToolbar tb = findViewById(R.id.toolbarGebreken);
        setSupportActionBar(tb);
        tb.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        tb.setNavigationOnClickListener(v -> finish());

        recycler = findViewById(R.id.recyclerGebreken);
        // recycler.setAdapter(...); // <- laat jouw bestaande adapter hier staan

        attachSwipeHelpers();
    }

    private void attachSwipeHelpers() {
        ItemTouchHelper ith = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                   @NonNull RecyclerView.ViewHolder viewHolder,
                                   @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                int pos = vh.getAdapterPosition();
                if (direction == ItemTouchHelper.LEFT) {
                    // Verwijderen + UNDO (TODO: haak in op jouw adapter/DB)
                    Snackbar.make(recycler, "Gebrek verwijderen (TODO)", Snackbar.LENGTH_LONG)
                            .setAction("UNDO", v -> {
                                // Reset swipe UI
                                RecyclerView.Adapter a = recycler.getAdapter();
                                if (a != null) a.notifyItemChanged(pos);
                            })
                            .addCallback(new Snackbar.Callback() {
                                @Override public void onDismissed(Snackbar bar, int event) {
                                    if (event != DISMISS_EVENT_ACTION) {
                                        // TODO: definitief verwijderen in DB/adapter
                                        RecyclerView.Adapter a = recycler.getAdapter();
                                        if (a != null) a.notifyItemChanged(pos);
                                    }
                                }
                            })
                            .show();
                } else if (direction == ItemTouchHelper.RIGHT) {
                    // Bewerken (TODO: toon jouw bewerk-dialoog)
                    RecyclerView.Adapter a = recycler.getAdapter();
                    if (a != null) a.notifyItemChanged(pos); // reset swipe UI
                    // TODO: openEditDialogFor(pos);
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv,
                                    @NonNull RecyclerView.ViewHolder vh, float dX, float dY,
                                    int state, boolean isActive) {
                super.onChildDraw(c, rv, vh, dX, dY, state, isActive);
                if (state != ItemTouchHelper.ACTION_STATE_SWIPE) return;
                float height = vh.itemView.getBottom() - vh.itemView.getTop();
                RectF bg;
                Paint p = new Paint();
                if (dX < 0) { // links = delete (rood)
                    p.setColor(0xFFE53935);
                    bg = new RectF(vh.itemView.getRight() + dX, vh.itemView.getTop(),
                                   vh.itemView.getRight(), vh.itemView.getBottom());
                    c.drawRect(bg, p);
                } else if (dX > 0) { // rechts = edit (blauw)
                    p.setColor(0xFF1E88E5);
                    bg = new RectF(vh.itemView.getLeft(), vh.itemView.getTop(),
                                   vh.itemView.getLeft() + dX, vh.itemView.getBottom());
                    c.drawRect(bg, p);
                }
            }
        });
        ith.attachToRecyclerView(recycler);
    }
}
