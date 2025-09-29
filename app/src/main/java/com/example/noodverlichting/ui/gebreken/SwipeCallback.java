package com.example.noodverlichting.ui.gebreken;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.example.noodverlichting.R;

public abstract class SwipeCallback extends ItemTouchHelper.SimpleCallback {

    private final Paint paint = new Paint();
    private final Drawable delete, edit;
    private final int colorDelete, colorEdit;

    public SwipeCallback(Context ctx, int swipeDirs) {
        super(0, swipeDirs);
        delete = safeDrawable(ctx, R.drawable.ic_delete_24);
        edit   = safeDrawable(ctx, R.drawable.ic_edit_24);
        colorDelete = 0xFFE53935; // rood 600
        colorEdit   = 0xFF1E88E5; // blauw 600
    }

    private Drawable safeDrawable(Context ctx, int resId) {
        try { return ContextCompat.getDrawable(ctx, resId); } catch (Exception ex) { return null; }
    }

    @Override public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder t) {
        return false;
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                            float dX, float dY, int state, boolean active) {
        super.onChildDraw(c, rv, vh, dX, dY, state, active);
        if (state != ItemTouchHelper.ACTION_STATE_SWIPE) return;

        float height = vh.itemView.getBottom() - vh.itemView.getTop();
        float width = height / 3f; // icon‑marge
        RectF bg;

        if (dX < 0) { // links = DELETE
            paint.setColor(colorDelete);
            bg = new RectF(vh.itemView.getRight() + dX, vh.itemView.getTop(), vh.itemView.getRight(), vh.itemView.getBottom());
            c.drawRect(bg, paint);
            if (delete != null) {
                int iconSize = (int)(height * 0.4f);
                int left = (int)(vh.itemView.getRight() - width - iconSize);
                int top  = (int)(vh.itemView.getTop() + (height - iconSize)/2f);
                delete.setBounds(left, top, left + iconSize, top + iconSize);
                delete.draw(c);
            }
        } else if (dX > 0) { // rechts = EDIT
            paint.setColor(colorEdit);
            bg = new RectF(vh.itemView.getLeft(), vh.itemView.getTop(), vh.itemView.getLeft() + dX, vh.itemView.getBottom());
            c.drawRect(bg, paint);
            if (edit != null) {
                int iconSize = (int)(height * 0.4f);
                int left = (int)(vh.itemView.getLeft() + width);
                int top  = (int)(vh.itemView.getTop() + (height - iconSize)/2f);
                edit.setBounds(left, top, left + iconSize, top + iconSize);
                edit.draw(c);
            }
        }
    }
}

