
package nl.mikekemmink.noodverlichting.ui;

import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.GestureDetectorCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.columns.ColumnConfig;

public class FixtureRowAdapter extends CursorAdapter {
    public interface RowLongClickListener { void onRowLongClick(int inspectieId, String fixtureCode, int position); }

    private final LayoutInflater inflater;
    private final List<ColumnConfig> config;
    private final ScrollSyncManager syncManager;
    private final RowLongClickListener longClickListener;
    private final Map<String, Integer> colWidthsPx;
    private final FixtureListActivity.DefectProvider defectProvider;
    private boolean showDefects;

    public FixtureRowAdapter(Context ctx, Cursor c, List<ColumnConfig> config,
                             ScrollSyncManager syncManager, RowLongClickListener longClickListener,
                             Map<String, Integer> colWidthsPx,
                             FixtureListActivity.DefectProvider defectProvider,
                             boolean showDefects) {
        super(ctx, c, 0);
        this.inflater = LayoutInflater.from(ctx);
        this.config = new ArrayList<>();
        for (ColumnConfig cc : config) if (cc.visible) this.config.add(cc);
        this.syncManager = syncManager;
        this.longClickListener = longClickListener;
        this.colWidthsPx = colWidthsPx;
        this.defectProvider = defectProvider;
        this.showDefects = showDefects;
    }

    public void setShowDefects(boolean show) {
        if (this.showDefects != show) {
            this.showDefects = show;
            notifyDataSetChanged();
        }
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View v = inflater.inflate(R.layout.row_fixture_wide, parent, false);
        ViewHolder h = new ViewHolder(v);
        v.setTag(h);
        v.setLongClickable(true);
        v.setClickable(true);
        v.setFocusable(false);
        if (h.rowContainer != null) {
            h.rowContainer.setClickable(false);
            h.rowContainer.setLongClickable(false);
            h.rowContainer.setFocusable(false);
        }
        View sv = v.findViewById(R.id.hscrollRow);
        if (sv instanceof SyncHorizontalScrollView && syncManager != null) {
            ((SyncHorizontalScrollView) sv).setSyncer(syncManager);
        }
        final GestureDetectorCompat detector = new GestureDetectorCompat(context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override public void onLongPress(MotionEvent e) {
                        ViewHolder vh = (ViewHolder) v.getTag();
                        if (vh != null && longClickListener != null) {
                            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                            longClickListener.onRowLongClick(vh.inspectieId, vh.fixtureCode, vh.position);
                        }
                    }
                });
        v.setOnTouchListener((view, event) -> { detector.onTouchEvent(event); return false; });
        v.setOnLongClickListener(view -> {
            ViewHolder vh = (ViewHolder) view.getTag();
            if (vh != null && longClickListener != null) {
                longClickListener.onRowLongClick(vh.inspectieId, vh.fixtureCode, vh.position);
                return true;
            }
            return false;
        });
        return v;
    }

    @Override
    public void bindView(View view, Context context, Cursor c) {
        ViewHolder h = (ViewHolder) view.getTag();
        h.position = c.getPosition();
        int idxId = c.getColumnIndex("_id");
        int idxCode = c.getColumnIndex("code");
        h.inspectieId = (idxId >= 0) ? c.getInt(idxId) : -1;
        h.fixtureCode = (idxCode >= 0) ? c.getString(idxCode) : null;

        for (TextView tv : h.cells) if (tv != null) { tv.setText(""); tv.setVisibility(View.GONE); }
        h.rowContainer.removeAllViews();
        boolean anyAdded = false;

        for (ColumnConfig cc : config) {
            int idx = indexOfAlias(cc.alias);
            if (idx < 0) continue;
            int colIndex = c.getColumnIndex(cc.alias);
            String value = null;
            if (colIndex >= 0) { try { value = c.getString(colIndex); } catch (Exception ignored) {} }
            addCell(h, h.cells[idx], value, cc.alias);
            anyAdded = true;
        }

        // Dynamische 'Gebreken' kolom bij toggle
        if (showDefects) {
            String summary = defectProvider != null ? defectProvider.summaryFor(h.inspectieId) : null;
            TextView dyn = createCellTextView(context);
            addCell(h, dyn, (summary == null || summary.trim().isEmpty()) ? "—" : summary, "gebreken");
        }

        if (!anyAdded && !showDefects) {
            int[] tryAliasOrder = { indexOfAlias("code"), indexOfAlias("nr"), indexOfAlias("soort") };
            for (int idx : tryAliasOrder) {
                if (idx < 0) continue;
                int colIndex = c.getColumnIndex(ALIAS[idx]);
                String value = null;
                if (colIndex >= 0) { try { value = c.getString(colIndex); } catch (Exception ignored) {} }
                addCell(h, h.cells[idx], value, ALIAS[idx]);
                break;
            }
        }
    }

    private TextView createCellTextView(Context ctx) {
        TextView tv = new TextView(ctx);
        tv.setTextColor(0xFF222222);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        return tv;
    }

    private void addCell(ViewHolder h, TextView tv, String value, String alias) {
        if (tv == null || h.rowContainer == null) return;
        Integer w = (colWidthsPx != null ? colWidthsPx.get(alias) : null);
        int widthPx = (w != null ? w : ViewGroup.LayoutParams.WRAP_CONTENT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
        tv.setLayoutParams(lp);
        tv.setMinWidth(widthPx);
        tv.setMaxWidth(widthPx);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        ViewGroup parent = (ViewGroup) tv.getParent();
        if (parent != null && parent != h.rowContainer) parent.removeView(tv);
        if (value == null || value.trim().isEmpty()) { tv.setText("—"); tv.setAlpha(0.6f); }
        else { tv.setText(value); tv.setAlpha(1f); }
        tv.setVisibility(View.VISIBLE);
        h.rowContainer.addView(tv);
    }

    private static final String[] ALIAS = new String[] {
            "nr","code","soort","verdieping","op_tekening","type","merk","montagewijze",
            "pictogram","accutype","artikelnr","accu_leeftijd","ats","duurtest","opmerking"
    };
    private static final int[] VIEW_ID = new int[] {
            R.id.tvNr, R.id.tvCode, R.id.tvSoort, R.id.tvVerdieping, R.id.tvOpTekening, R.id.tvType, R.id.tvMerk, R.id.tvMontagewijze,
            R.id.tvPictogram, R.id.tvAccuType, R.id.tvArtikelNr, R.id.tvAccuLeeftijd, R.id.tvATS, R.id.tvDuurtest, R.id.tvOpmerking
    };
    private static int indexOfAlias(String alias) {
        if (alias == null) return -1;
        String needle = alias.trim();
        for (int i = 0; i < ALIAS.length; i++) if (ALIAS[i].equalsIgnoreCase(needle)) return i;
        return -1;
    }

    static class ViewHolder {
        final LinearLayout rowContainer;
        final TextView[] cells = new TextView[VIEW_ID.length];
        int position; int inspectieId; String fixtureCode;
        ViewHolder(View root) {
            rowContainer = root.findViewById(R.id.rowContainer);
            for (int i = 0; i < VIEW_ID.length; i++) cells[i] = root.findViewById(VIEW_ID[i]);
        }
    }
}
