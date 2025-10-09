package nl.mikekemmink.noodverlichting.noodverlichting;

import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.core.view.GestureDetectorCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.noodverlichting.columns.ColumnConfig;

public class FixtureRowAdapter extends CursorAdapter {

    public interface RowLongClickListener {
        void onRowLongClick(int inspectieid, String fixtureCode, int position);
    }
    public interface RowClickListener {
        void onRowClick(int inspectieid);
    }

    private final RowClickListener clickListener;
    private final LayoutInflater inflater;
    private List<ColumnConfig> config = Collections.emptyList();
    private final ScrollSyncManager syncManager;
    private final RowLongClickListener longClickListener;
    private final Map<String, Integer> colWidthsPx;
    private final FixtureListActivity.DefectProvider defectProvider;
    private boolean showDefects;

    // --- Visuele selectie (blijvende highlight) ---
    private int activatedInspectieId = -1;

    public FixtureRowAdapter(Context ctx, Cursor c, List<ColumnConfig> config,
                             ScrollSyncManager syncManager,
                             RowLongClickListener longClickListener,
                             RowClickListener clickListener,
                             Map<String, Integer> colWidthsPx,
                             FixtureListActivity.DefectProvider defectProvider,
                             boolean showDefects) {
        super(ctx, c, 0);
        this.inflater = LayoutInflater.from(ctx);
        this.config = new ArrayList<>();
        for (ColumnConfig cc : config) {
            if (cc.visible) this.config.add(cc);
        }
        this.syncManager = syncManager;
        this.longClickListener = longClickListener;
        this.clickListener = clickListener;
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

    /** Laat host (bijv. Activity) de geactiveerde rij zetten o.b.v. inspectieid. */
    public void setActivatedInspectieId(int id) {
        if (this.activatedInspectieId != id) {
            this.activatedInspectieId = id;
            notifyDataSetChanged();
        }
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View v = inflater.inflate(R.layout.nv_row_fixture_wide, parent, false);
        ViewHolder h = new ViewHolder(v);
        v.setTag(h);

        // Alleen de root is clickable; kinderen niet → background-selector krijgt alle states
        v.setClickable(true);
        v.setLongClickable(true);
        v.setFocusable(false);
        if (h.rowContainer != null) {
            h.rowContainer.setClickable(false);
            h.rowContainer.setLongClickable(false);
            h.rowContainer.setFocusable(false);
        }

        // Horizontale scroller binnen de rij
        View sv = v.findViewById(R.id.hscrollRow);

        // Centrale korte-klik → zet activated + laat plattegrond meeschakelen

        View.OnClickListener rowClick = view2 -> {
            ViewHolder vh = (ViewHolder) v.getTag();
            if (vh != null && clickListener != null) {
                Log.d("ArmatuurKlik", "RowClick via root, inspectieid = " + vh.inspectieid);

                // 1) Onthoud geselecteerde armatuur
                activatedInspectieId = vh.inspectieid;

                // 2) DIRECTE visuele feedback die blijft (tot een nieuwe selectie)
                v.setActivated(true);
                v.setSelected(true);
                v.jumpDrawablesToCurrentState(); // forceer state-update meteen

                // 3) Herteken lijst zodat eerdere geselecteerde rij zijn activated kwijtraakt
                notifyDataSetChanged();

                // 4) Plattegrond laten meeschakelen
                clickListener.onRowClick(vh.inspectieid);
            }
        };


        if (clickListener != null) {
             v.setOnClickListener(rowClick);
             v.setClickable(true);
         } else {
             // Laat ListView de klik afhandelen
             v.setOnClickListener(null);
             v.setClickable(false);
         }


        // Scroll sync
        if (sv instanceof SyncHorizontalScrollView && syncManager != null) {
            ((SyncHorizontalScrollView) sv).setSyncer(syncManager);
        }

        // Eén GestureDetector aan root, scroller en container (long-press + single-tap)
        final GestureDetectorCompat detector = new GestureDetectorCompat(
                context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public void onLongPress(MotionEvent e) {
                        ViewHolder vh = (ViewHolder) v.getTag();
                        if (vh != null && longClickListener != null) {
                            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                            longClickListener.onRowLongClick(vh.inspectieid, vh.fixtureCode, vh.position);
                        }
                    }
                    @Override
                    public boolean onSingleTapUp(MotionEvent e) {
                        // Als de adapter zélf kliks afhandelt, gebruik het eigen pad
                        if (clickListener != null) {
                            v.performClick();
                            return true; // consume: adapter-klik is afgehandeld
                        }

                        // ListView-modus (clickListener == null) -> klik expliciet doorzetten
                        View parent = (View) v.getParent();
                        ViewHolder vh = (ViewHolder) v.getTag();

                        // Veiligheid: als de ouder een ListView is, trigger de item click
                        if (parent instanceof ListView) {
                            ListView lv = (ListView) parent;
                            int position = (vh != null) ? vh.position : lv.getPositionForView(v);
                            long itemId = position; // CursorAdapter gebruikt positie als id oké
                            lv.performItemClick(v, position, itemId);
                            return true; // consume: we hebben de klik al afgehandeld
                        }

                        // Als er geen ListView-ouder is, laat bubbelen (fallback)
                        return false;
                    }
                });

        // >>> PRESSED-STATE MANAGER <<<
        final int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        final float[] down = new float[2];

        View.OnTouchListener pressState = (viewX, event) -> {
            // Altijd doorgeven aan detector (voor long-press + single-tap -> performClick)
            detector.onTouchEvent(event);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = event.getX();
                    down[1] = event.getY();
                    // Zet pressed op de ROOT van de rij (achtergrond is op root gezet)
                    v.setPressed(true);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(event.getX() - down[0]) > touchSlop ||
                            Math.abs(event.getY() - down[1]) > touchSlop) {
                        // Zodra er gescrolld wordt → pressed uit
                        v.setPressed(false);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    break;
            }
            // Belangrijk: false zodat click/scroll gewoon blijven werken
            return false;
        };

        // Koppel dezelfde touch-listener overal waar de gebruiker kan aanraken
        v.setOnTouchListener(pressState);
        if (sv != null) {
            sv.setClickable(false);           // klik niet op scroller zelf afhandelen
            sv.setLongClickable(false);
            sv.setOnTouchListener(pressState);
        }
        if (h.rowContainer != null) {
            h.rowContainer.setOnTouchListener(pressState);
        }

        return v;
    }


    @Override
    public void bindView(View view, Context context, Cursor c) {
        ViewHolder h = (ViewHolder) view.getTag();
        h.position = c.getPosition();

        int idxId = c.getColumnIndex("inspectieid");
        h.inspectieid = (idxId >= 0) ? c.getInt(idxId) : -1;

        int idxCode = c.getColumnIndex("code");
        h.fixtureCode = (idxCode >= 0) ? c.getString(idxCode) : null;

        // >>> BLIJVENDE HIGHLIGHT <<<
        final boolean selected = (h.inspectieid == activatedInspectieId);
        view.setActivated(selected);
        view.setSelected(selected);
        view.jumpDrawablesToCurrentState();

        // Reset en (her)opbouw cellen
        for (TextView tv : h.cells) if (tv != null) { tv.setText(""); tv.setVisibility(View.GONE); }
        h.rowContainer.removeAllViews();
        boolean anyAdded = false;

        Log.d("ArmatuurKlik", "bindView: inspectieid = " + h.inspectieid);

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
            String summary = defectProvider != null ? defectProvider.summaryFor(h.inspectieid) : null;
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
            "inspectieid","nr","code","soort","verdieping","op_tekening","type","merk","montagewijze",
            "pictogram","accutype","artikelnr","accu_leeftijd","ats","duurtest","opmerking"
    };
    private static final int[] VIEW_ID = new int[] {
            R.id.tvinspectieid, R.id.tvNr, R.id.tvCode, R.id.tvSoort, R.id.tvVerdieping, R.id.tvOpTekening, R.id.tvType, R.id.tvMerk, R.id.tvMontagewijze,
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
        int position; int inspectieid; String fixtureCode;
        ViewHolder(View root) {
            rowContainer = root.findViewById(R.id.rowContainer);
            for (int i = 0; i < VIEW_ID.length; i++) cells[i] = root.findViewById(VIEW_ID[i]);
        }
    }
}