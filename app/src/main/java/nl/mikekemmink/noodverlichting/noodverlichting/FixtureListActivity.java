package nl.mikekemmink.noodverlichting.noodverlichting;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.github.barteksc.pdfviewer.PDFView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.noodverlichting.columns.ColumnConfig;
import nl.mikekemmink.noodverlichting.noodverlichting.columns.ColumnConfigManager;
import nl.mikekemmink.noodverlichting.noodverlichting.columns.ColumnSettingsActivity;
import nl.mikekemmink.noodverlichting.noodverlichting.data.DBHelper;
import nl.mikekemmink.noodverlichting.noodverlichting.pdf.PdfPageCache;
import nl.mikekemmink.noodverlichting.ui.BaseToolbarActivity;

public class FixtureListActivity extends BaseToolbarActivity {

    private void invalidateDefectCachesAndRefresh() {
        // Cache voor snelle booleans (hasDefects) leeg
        defectFlagCache.clear();

        // LruCache met summaries leeg
        if (defectProvider != null) {
            defectProvider.invalidate();
        }

        // Lijst/kolom ‘Gebreken’ up‑to‑date
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }

        // PDF‑overlay (markers) opnieuw tekenen
        if (pdfView != null) {
            pdfView.postInvalidateOnAnimation();
        }
    }

    // Animatie voor pulserende ring
    private android.animation.ValueAnimator markerPulse;
    private float pulse; // 0..1 progress

    // Preferenties om laatste plattegrond te onthouden
    private static final String KEY_LAST_PDF = "last_pdf";
    private static final String KEY_LAST_PDF_PAGE = "last_pdf_page";

    // (optioneel) zodat we warm-open maar één keer per Activity doen
    private boolean didWarmOpen = false;

    private static final String TAG = "FixtureListActivity";
    private static final String PREFS = "app_prefs";
    private static final String KEY_LAST_LOCATIE = "last_locatie";

    private List<MarkerData> alleMarkers = new ArrayList<>();
    private int geselecteerdeInspectieId = -1;

    private final Map<String, File> pdfCache = new HashMap<>();
    private DBHelper dbHelper;
    private Cursor cursor;

    private ListView list;
    private FixtureRowAdapter adapter;
    private ScrollSyncManager syncManager = new ScrollSyncManager();
    private View headerView;

    private boolean showDefects = false; // toolbar-toggle
    private DefectProvider defectProvider;

    private Map<Integer, MarkerData> markerMap = new HashMap<>();

    // === BESTANDSLOCATIES ===
    private File getMarkersDir() { return new File(getFilesDir(), "markers"); }
    private File getPlattegrondenDir() { return new File(getFilesDir(), "plattegronden"); }
    private File markersJsonFile() { return new File(getMarkersDir(), "markers.json"); }
    private File pdfFileForName(String pdfNaam) { return new File(getPlattegrondenDir(), pdfNaam); }

    // Viewer/state
    private View plattegrondView;
    private PDFView pdfView;
    private File currentPdf = null;
    private int currentPageIndex = -1;
    private ImageView pdfPlaceholder;

    // markers index per (pdf+page)
    private final Map<String, List<MarkerData>> markersByPdfPage = new HashMap<>();
    private static String key(String pdfNaam, int pageIndex) {
        return (pdfNaam == null ? "" : pdfNaam).toLowerCase(Locale.ROOT) + "::" + pageIndex;
    }
    // Cache: inspectieId -> heeftGebreken (true/false)
    private final Map<Integer, Boolean> defectFlagCache = new HashMap<>();
    // --- Puls-config ---
    private static final long  PULSE_DURATION_MS       = 5000; // duur van één puls (ms)
    private static final int   PULSE_PULSES            = 5;     // aantal pulsen: 1..3 gebruikelijk
    private static final float RING_START_SCALE        = 1.12f; // ring start 12% groter dan marker
    private static final float RING_MAX_EXTRA_SCALE    = 0.95f; // extra groei t.o.v. start (0.95 ≈ 95%)
    private static final float RING_STROKE_DP          = 3.5f;  // dikte van de ringlijn
    private static final int   RING_ALPHA_MIN          = 48;    // minimale zichtbaarheid (0..255)
    private static final float ALPHA_EASE_EXP          = 1.2f;  // lager = langzamere fade

    // Interne vlag: is er een puls bezig?
    private boolean pulseActive = false;
    /** Puls-animatie starten en vloeiend redraw forceren. */
    private void startMarkerPulse() {
        if (markerPulse != null) markerPulse.cancel();

        pulseActive = true;
        markerPulse = android.animation.ValueAnimator.ofFloat(0f, 1f);
        markerPulse.setDuration(PULSE_DURATION_MS);
        markerPulse.setRepeatMode(android.animation.ValueAnimator.RESTART);
        markerPulse.setRepeatCount(Math.max(0, PULSE_PULSES - 1));
        markerPulse.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());

        markerPulse.addUpdateListener(a -> {
            pulse = (float) a.getAnimatedValue();
            if (pdfView != null) pdfView.postInvalidateOnAnimation();
        });

        markerPulse.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                pulseActive = false;
                if (pdfView != null) pdfView.postInvalidateOnAnimation();
            }
            @Override public void onAnimationCancel(android.animation.Animator animation) {
                pulseActive = false;
                if (pdfView != null) pdfView.postInvalidateOnAnimation();
            }
        });

        markerPulse.start();
    }

    private void warmOpenLastPdfIfAny() {
        String lastPdfName = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_LAST_PDF, null);
        int lastPage = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_LAST_PDF_PAGE, 0);
        if (lastPdfName == null) return;
        File pdfFile = pdfFileForName(lastPdfName);
        if (pdfFile == null || !pdfFile.exists()) return;

        // Zorg dat de plattegrond-view bestaat, maar laat de container onzichtbaar
        FrameLayout container = findViewById(R.id.plattegrondContainer);
        if (container == null) return;
        int originalVisibility = container.getVisibility(); // meestal GONE
        ensurePlattegrondView(container);
        container.setVisibility(View.GONE);

        // Zet state en warm-open de PDF inclusief marker overlay
        currentPdf = pdfFile;
        currentPageIndex = Math.max(0, lastPage);

        pdfView.recycle();
        pdfView.fromFile(currentPdf)
                .defaultPage(currentPageIndex)
                .enableSwipe(false)
                .fitEachPage(true)
                .spacing(0)
                // ✔ Gedeelde overlay (incl. puls)
                .onDraw((canvas, pageWidth, pageHeight, displayedPage) ->
                        drawMarkersOverlay(canvas, pageWidth, pageHeight, displayedPage))
                .onLoad(nb -> {
                    pdfView.setMinZoom(0.5f);
                    pdfView.setMidZoom(2.0f);
                    pdfView.setMaxZoom(8.0f);
                    pdfView.enableAntialiasing(true);
                })
                // ✔ Tap-selectie marker → lijstselectie
                .onTap(e -> {
                    MarkerData hit = hitTestMarker(e.getX(), e.getY());
                    if (hit != null) {
                        selectFromMarker(hit);
                        return true;
                    }
                    return false;
                })
                .load();

        // Prefetch de actuele pagina naar disk-cache (best-effort)
        final int w = getResources().getDisplayMetrics().widthPixels;
        new Thread(() -> {
            try {
                PdfPageCache.prefetch(this, currentPdf, new int[]{ currentPageIndex }, w);
            } catch (Throwable ignore) {}
        }).start();

        // container zichtbaar laten zoals hij stond
        container.setVisibility(originalVisibility);
    }

    private void preloadPdfPlattegronden() {
        Set<String> uniekePdfNamen = new HashSet<>();
        for (MarkerData marker : markerMap.values()) {
            if (marker.pdfNaam != null && !marker.pdfNaam.trim().isEmpty()) {
                uniekePdfNamen.add(marker.pdfNaam);
            }
        }
        File base = getPlattegrondenDir();
        if (!base.exists()) base.mkdirs();
        for (String pdfNaam : uniekePdfNamen) {
            File pdfFile = pdfFileForName(pdfNaam);
            if (pdfFile.exists()) {
                pdfCache.put(pdfNaam, pdfFile);
            }
        }
    }

    private void ensureMarkersAvailable() {
        File dest = markersJsonFile();
        if (dest.exists() && dest.length() > 0) return;
        try {
            if (!dest.getParentFile().exists()) dest.getParentFile().mkdirs();
            InputStream in;
            try { in = getAssets().open("markers/markers.json"); }
            catch (Exception e1) { in = getAssets().open("markers.json"); }
            try (InputStream is = in; OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[4096]; int n; while ((n = is.read(buf)) > 0) out.write(buf, 0, n);
            }
        } catch (Exception e) { Log.e(TAG, "Kon markers.json niet beschikbaar maken", e); }
    }

    private void loadMarkersFromJson() {
        markerMap.clear(); alleMarkers.clear(); markersByPdfPage.clear();
        ensureMarkersAvailable();
        File f = markersJsonFile(); if (!f.exists() || f.length()==0) return;
        String json;
        try (InputStream in = new FileInputStream(f);
             InputStreamReader isr = new InputStreamReader(in, StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(isr)) {
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            json = sb.toString();
        } catch (Exception e) { Log.e(TAG, "Lezen markers.json mislukt", e); return; }

        try {
            if (json.startsWith("\uFEFF")) json = json.substring(1);
            json = json.trim();
            JSONArray array = json.startsWith("[") ? new JSONArray(json) : new JSONArray().put(new JSONObject(json));
            for (int i=0;i<array.length();i++) {
                JSONObject obj = array.getJSONObject(i);
                long idL = optLongAny(obj, new String[]{"inspectie_id","inspectieId","id","fixture_id","armatuur_id"}, -1L);
                int id = (idL>=Integer.MIN_VALUE && idL<=Integer.MAX_VALUE) ? (int) idL : -1;
                String rawPdf = optStringAny(obj, new String[]{"pdf_naam","pdfNaam","pdf","pdfName"}, "");
                String pdfNaam = rawPdf!=null? rawPdf.trim():"";
                int pageIndex = 0; int page1 = optIntAny(obj, new String[]{"page","pagina","pageIndex1"}, 0);
                if (page1>0) pageIndex = Math.max(0, page1-1);
                int hash = pdfNaam.lastIndexOf("#p");
                if (hash>=0 && hash+2<pdfNaam.length()) {
                    try { int oneBased = Integer.parseInt(pdfNaam.substring(hash+2)); pageIndex=Math.max(0,oneBased-1); pdfNaam=pdfNaam.substring(0,hash);} catch(Exception ignore){}
                }
                if (!pdfNaam.toLowerCase(Locale.ROOT).endsWith(".pdf") && !pdfNaam.isEmpty()) pdfNaam += ".pdf";
                double x = optDoubleAny(obj,new String[]{"x","fx","px"},Double.NaN);
                double y = optDoubleAny(obj,new String[]{"y","fy","py"},Double.NaN);
                if (Double.isNaN(x) || Double.isNaN(y) || pdfNaam.isEmpty()) continue;
                MarkerData md = new MarkerData(pdfNaam,x,y,pageIndex,id);
                alleMarkers.add(md); if (id>=0) markerMap.put(id, md);
                String k = key(pdfNaam,pageIndex); List<MarkerData> lst = markersByPdfPage.get(k);
                if (lst==null){lst=new ArrayList<>(); markersByPdfPage.put(k,lst);} lst.add(md);
            }
        } catch (Exception e) { Log.e(TAG, "Fout in markers.json", e); }
    }


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1) Alleen je content inflaten onder de gedeelde toolbar
        setContentLayout(R.layout.activity_fixture_list);

        // 2) Toolbar-titel/palet/up-knop via BaseToolbarActivity
        setTitle("Armaturen");                 // wordt zo meteen overschreven door locatie-naam
        applyPalette(Palette.NOOD);            // of Palette.NEN als dit scherm bij NEN3140 hoort
        setUpEnabled(true);

        // --- vanaf hier je bestaande init (ongelaten) ---
        String locatie = getIntent()!=null ? getIntent().getStringExtra(LocationListActivity.EXTRA_LOCATIE): null;
        if (locatie==null || locatie.trim().isEmpty())
            locatie = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_LAST_LOCATIE, null);
        if (locatie==null || locatie.trim().isEmpty()) { startActivity(new Intent(this, LocationListActivity.class)); finish(); return; }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_LAST_LOCATIE, locatie).apply();

        if (getSupportActionBar()!=null) getSupportActionBar().setTitle(locatie);

        dbHelper = new DBHelper(this);
        defectProvider = new DefectProvider(this);

        list = findViewById(R.id.listArmaturen);

        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (adapter == null) return;
                Cursor c = (Cursor) adapter.getItem(position);
                if (c == null) return;
                int idx = c.getColumnIndex("inspectieid");
                if (idx >= 0) {
                    int inspectieId = c.getInt(idx);

                    // ▼ visuele feedback in de lijst (blijvende highlight + ripple)
                    list.setItemChecked(position, true);

                    // ▼ bron van waarheid + adapterstate
                    geselecteerdeInspectieId = inspectieId;
                    adapter.setActivatedInspectieId(geselecteerdeInspectieId);

                    // ▼ Plattegrond tonen/centreren
                    showPlattegrondWithMarker(inspectieId);
                }
            }
        });

        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (adapter==null) return true; Cursor c = (Cursor) adapter.getItem(position); if (c==null) return true;
                int idxId = c.getColumnIndex("inspectieid"); if (idxId<0) return true; int idxCode = c.getColumnIndex("code");
                int inspectieid = c.getInt(idxId); String fixtureCode = (idxCode>=0? c.getString(idxCode): null);
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); showRowMenu(inspectieid, fixtureCode); return true;
            }
        });

        ensureMarkersAvailable();
        loadMarkersFromJson();
        preloadPdfPlattegronden();
        load(locatie);
    }

    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); reload(); }

    @Override
    protected void onResume() {
        super.onResume();

        // Caches invalideren + UI verversen zodat marker-kleuren kloppen
        defectFlagCache.clear();            // jouw boolean cache voor hasDefects(...)
        if (defectProvider != null) defectProvider.invalidate();
        if (adapter != null) adapter.notifyDataSetChanged();      // lijstkolom ‘Gebreken’
        if (pdfView != null) pdfView.postInvalidateOnAnimation(); // marker overlay


        if (!didWarmOpen) { warmOpenLastPdfIfAny(); didWarmOpen = true; }
        reload();
    }

    @Override protected void onPause() { super.onPause(); }

    private void ensurePlattegrondView(FrameLayout container) {
        if (plattegrondView!=null && plattegrondView.getParent()==container && pdfView!=null) return;
        container.removeAllViews();
        plattegrondView = getLayoutInflater().inflate(R.layout.fragment_plattegrond, container, false);
        container.addView(plattegrondView);
        pdfView = plattegrondView.findViewById(R.id.pdfView);
        pdfView = plattegrondView.findViewById(R.id.pdfView);
        pdfPlaceholder = plattegrondView.findViewById(R.id.pdfPlaceholder);
    }

    /** Overlay voor markers + pulserende ring. */
    private void drawMarkersOverlay(android.graphics.Canvas canvas,
                                    float pageWidth, float pageHeight, int displayedPage) {
        if (pdfView == null || currentPdf == null) return;

        List<MarkerData> lijst = markersByPdfPage.get(key(currentPdf.getName(), displayedPage));
        if (lijst == null || lijst.isEmpty()) return;

        final float zoom = Math.max(1f, pdfView.getZoom());

        for (MarkerData m : lijst) {
            float cx = (float) (m.x * pageWidth);
            float cy = (float) (m.y * pageHeight);

            float rDp = Math.max(8f, Math.min(22f, 8f + (zoom - 2f) * 2f));
            float rPx = dp((int) rDp);

            // Kleurkeuze:
            // - geselecteerd: BLAUW
            // - anders als er gebreken zijn: ROOD
            // - anders: GROEN (standaard)
            int fillColor;
            if (m.inspectieid == geselecteerdeInspectieId) {
                fillColor = 0xFF2196F3;           // Material Blue 500
            } else if (hasDefects(m.inspectieid)) {
                fillColor = 0xFFE53935;           // Material Red 600
            } else {
                fillColor = 0xFF4CAF50;           // Material Green 500
            }

            Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(fillColor);

            Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setColor(0xFFFFFFFF);
            stroke.setStrokeWidth(dp(2) / zoom);

            canvas.save();
            canvas.scale(zoom, zoom, cx, cy);
            float rCanvas = rPx / zoom;

            canvas.drawCircle(cx, cy, rCanvas, fill);
            canvas.drawCircle(cx, cy, rCanvas, stroke);

            // Pulserende ring op geselecteerde marker
            // Pulserende ring op geselecteerde marker
            if (m.inspectieid == geselecteerdeInspectieId && pulseActive) {
                // 1) Radius: start > marker-radius, groeit naar buiten mee
                float scale = RING_START_SCALE + (RING_MAX_EXTRA_SCALE * pulse); // bv. 1.12 .. ~2.07
                float ringR = rCanvas * scale;

                // 2) Alpha: soepele fade-out met een minimum (zichtbaar blijven)
                double ease = Math.pow(1f - pulse, ALPHA_EASE_EXP);             // 1..0
                int alpha = (int) (RING_ALPHA_MIN + (255 - RING_ALPHA_MIN) * ease);

                android.graphics.Paint ring = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                ring.setStyle(android.graphics.Paint.Style.STROKE);
                ring.setColor(0xFF2196F3);
                ring.setAlpha(alpha);
                ring.setStrokeWidth(dp((int) RING_STROKE_DP) / zoom);           // iets dikker

                canvas.drawCircle(cx, cy, ringR, ring);
            }
            canvas.restore();
        }
    }

    private void showPlattegrondWithMarker(int inspectieid) {
        MarkerData marker = markerMap.get(inspectieid);
        FrameLayout container = findViewById(R.id.plattegrondContainer);
        geselecteerdeInspectieId = inspectieid;

        // Start puls voor visuele feedback
        startMarkerPulse();

        if (container == null) return;
        container.setVisibility(View.VISIBLE);
        ensurePlattegrondView(container);

        if (marker == null || marker.pdfNaam == null || marker.pdfNaam.trim().isEmpty()) {
            Toast.makeText(this, "Geen markerdata", Toast.LENGTH_SHORT).show();
            return;
        }

        File pdfFile = pdfCache.get(marker.pdfNaam);
        if (pdfFile == null) {
            pdfFile = pdfFileForName(marker.pdfNaam);
            if (pdfFile.exists()) pdfCache.put(marker.pdfNaam, pdfFile);
        }
        if (pdfFile == null || !pdfFile.exists()) {
            Toast.makeText(this, "Plattegrond niet gevonden: " + marker.pdfNaam, Toast.LENGTH_SHORT).show();
            return;
        }

        // SNELTAK: zelfde PDF -> NIET herladen, alleen naar pagina + centreren
        if (currentPdf != null && currentPdf.equals(pdfFile)) {
            if (pdfPlaceholder != null) pdfPlaceholder.setVisibility(View.GONE);
            if (pdfView.getCurrentPage() != marker.pageIndex) {
                pdfView.jumpTo(marker.pageIndex, /*animate*/ false);
            }
            final MarkerData m = marker;
            pdfView.post(() -> {
                ensureMinZoomForCenter(3.0f);
                centerOnMarkerDocSpace(m);
            });
            currentPageIndex = marker.pageIndex;
            // Forceer overlay redraw (blauw + puls)
            pdfView.postInvalidateOnAnimation();
            return;
        }

        // ANDERE PDF -> placeholder + laden
        currentPdf = pdfFile;
        currentPageIndex = marker.pageIndex;

        // 1) Placeholder uit cache (best-effort)
        if (pdfPlaceholder != null) {
            pdfPlaceholder.setVisibility(View.VISIBLE);
            pdfPlaceholder.setImageDrawable(null);
            final File pdfF = currentPdf;
            final int pageIdx = currentPageIndex;
            int targetWidth = container.getWidth() > 0
                    ? container.getWidth()
                    : getResources().getDisplayMetrics().widthPixels;
            new Thread(() -> {
                try {
                    Bitmap bmp = PdfPageCache.getOrRender(this, pdfF, pageIdx, targetWidth);
                    runOnUiThread(() -> {
                        if (pdfF.equals(currentPdf) && pageIdx == currentPageIndex && pdfPlaceholder != null) {
                            pdfPlaceholder.setImageBitmap(bmp);
                        }
                    });
                } catch (Exception ignore) { /* placeholder is best-effort */ }
            }).start();
        }

        // 2) Echte PDF laden met overlay + tap
        pdfView.recycle();
        pdfView.fromFile(currentPdf)
                .defaultPage(currentPageIndex)
                .enableSwipe(false)
                .fitEachPage(true)
                .spacing(0)
                // ✔ Gedeelde overlay (incl. puls)
                .onDraw((canvas, pageWidth, pageHeight, displayedPage) ->
                        drawMarkersOverlay(canvas, pageWidth, pageHeight, displayedPage))
                .onLoad(nb -> {
                    pdfView.setMinZoom(0.5f);
                    pdfView.setMidZoom(2.0f);
                    pdfView.setMaxZoom(8.0f);
                    pdfView.enableAntialiasing(true);
                })
                // ✔ Tap-selectie marker → lijstselectie
                .onTap(e -> {
                    MarkerData hit = hitTestMarker(e.getX(), e.getY());
                    if (hit != null) {
                        selectFromMarker(hit);
                        return true;
                    }
                    return false;
                })
                .onRender(nb -> pdfView.post(() -> {
                    ensureMinZoomForCenter(3.0f);
                    centerOnMarkerDocSpace(marker);
                    if (pdfPlaceholder != null) pdfPlaceholder.setVisibility(View.GONE);
                }))
                .onError(t -> {
                    if (pdfPlaceholder != null) pdfPlaceholder.setVisibility(View.GONE);
                })
                .load();

        // Bewaar laatst geopende PDF + pagina
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_PDF, currentPdf != null ? currentPdf.getName() : null)
                .putInt(KEY_LAST_PDF_PAGE, Math.max(0, currentPageIndex))
                .apply();
    }

    private void ensureMinZoomForCenter(float minZoom) {
        float z = pdfView.getZoom();
        if (z < minZoom) { pdfView.zoomTo(minZoom); pdfView.loadPages(); }
    }

    /** Centreer op basis van absolute document-coördinaten (verticale stapeling). */
    private void centerOnMarkerDocSpace(MarkerData marker) {
        if (pdfView == null) return;
        int page = marker.pageIndex;

        // Som van alle paginahoogten vóór de doelpagina (document-Y)
        float sumHeights = 0f;
        for (int i = 0; i < page; i++) {
            sumHeights += pdfView.getPageSize(i).getHeight();
        }
        float zoom = pdfView.getZoom();
        float pageW = pdfView.getPageSize(page).getWidth();
        float pageH = pdfView.getPageSize(page).getHeight();

        float docX = (float)marker.x * pageW * zoom; // binnen pagina
        float docY = (sumHeights + (float)marker.y * pageH) * zoom; // totaal document

        float vx = pdfView.getWidth()/2f; float vy = pdfView.getHeight()/2f;
        float newX = vx - docX; float newY = vy - docY;

        pdfView.moveTo(newX, newY); pdfView.loadPages();
        Log.d(TAG, "centerDoc: page="+page+" zoom="+zoom+" doc=(" + docX + "," + docY + ") off=(" + newX + "," + newY + ")");
    }

    private void load(String locatie) {
        SQLiteDatabase db = dbHelper.getReadableDatabase(); Set<String> cols = getColumns(db, "inspecties");
        String inspectieid = firstExisting(cols, "inspectie_id", "id", "inspectieId", "inspectie", "_id", "Inspectie-ID");
        String locCol = firstExisting(cols, "Locatie","locatie","Location","location");
        String nrCol = firstExisting(cols, "Nr.","Nr","nr.","nr","Nummer","nummer","ArmatuurNr","armatuur_nr","armatuurnr");
        String codeCol = firstExisting(cols, "Code","code","ArmatuurCode","armatuurcode","arm_code");
        String soortCol = firstExisting(cols, "Soort","soort","Type","type");
        String verdiepingCol = firstExisting(cols, "Verdieping","verdieping","Floor","floor","Etage","etage");
        String opTekCol = firstExisting(cols, "Op tekening","Op_Tekening","OpTekening","op_tekening","optekening");
        String typeCol = firstExisting(cols, "Type","type", "Armatuur type");
        String merkCol = firstExisting(cols, "Merk","merk","Armatuur merk","armatuur_merk","ArmatuurMerk");
        String montageCol = firstExisting(cols, "Montagewijze","montagewijze","Montage","montage");
        String pictogramCol = firstExisting(cols, "Pictogram","pictogram");
        String accuTypeCol = firstExisting(cols, "Accutype","accutype","Accu type","accu_type","Batterijtype","batterijtype");
        String artikelNrCol = firstExisting(cols, "Artikelnr","ArtikelNr","Artikelnr.","artikelnummer","Artikelnummer","artikelnr","artikelnr.");
        String accuLeeftCol = firstExisting(cols, "Accu leeftijd","AccuLeeftijd","accu_leeftijd","Accu (leeftijd)","accu leeftijd");
        String atsCol = firstExisting(cols, "ATS","ats","Autotest","autotest");
        String duurtestCol = firstExisting(cols, "Duurtest","duurtest","Duurtest (min)","duurtest_min");
        String opmCol = firstExisting(cols, "Opmerking","opmerking","Notitie","notitie","Notes","notes");

        if (locCol==null) { Toast.makeText(this, "Kolom 'Locatie' niet gevonden", Toast.LENGTH_LONG).show(); return; }

        String sql = "SELECT rowid AS _id, "
                + sel(inspectieid, "inspectieid") +", "+ sel(nrCol, "nr") +", "+ sel(codeCol, "code") +", "+ sel(soortCol, "soort") +", "
                + sel(verdiepingCol, "verdieping") +", "+ sel(opTekCol, "op_tekening") +", "+ sel(typeCol, "type") +", "+ sel(merkCol, "merk") +", "+ sel(montageCol, "montagewijze") +", "
                + sel(pictogramCol, "pictogram") +", "+ sel(accuTypeCol, "accutype") +", "+ sel(artikelNrCol, "artikelnr") +", "+ sel(accuLeeftCol, "accu_leeftijd") +", "
                + sel(atsCol, "ats") +", "+ sel(duurtestCol, "duurtest") +", "+ sel(opmCol, "opmerking")
                + " FROM inspecties WHERE " + q(locCol) + " = ? ORDER BY " + (nrCol != null ? q(nrCol) : (codeCol != null ? q(codeCol) : "rowid"));

        if (list.getAdapter()!=null) list.setAdapter(null);

        Map<String,Integer> colWidthsPx = computeColumnWidthsPx();
        List<ColumnConfig> cfg = ColumnConfigManager.load(this); boolean any=false; for (ColumnConfig c0: cfg){ if (c0.visible){ any=true; break; }}
        if (!any) { cfg = ColumnConfigManager.getDefault(); ColumnConfigManager.save(this, cfg); }

        buildHeader(cfg, colWidthsPx, showDefects);

        if (cursor!=null && !cursor.isClosed()) cursor.close();
        cursor = db.rawQuery(sql, new String[]{ locatie });

        if (cursor!=null && cursor.getCount()>0) {
            adapter = new FixtureRowAdapter(this, cursor, cfg, syncManager,
                    (id, fixtureCode, position) -> showRowMenu(id, fixtureCode),
                    null, // klik via ListView's OnItemClickListener
                    colWidthsPx, defectProvider, showDefects);
            list.setAdapter(adapter);

            // ▼ Herstel de laatst gekozen selectie (blijvende highlight)
            if (geselecteerdeInspectieId != -1) {
                adapter.setActivatedInspectieId(geselecteerdeInspectieId);
                int pos = findPositionByInspectieId(geselecteerdeInspectieId);
                if (pos != -1) {
                    list.setItemChecked(pos, true);
                }
            }
        } else { adapter=null; list.setAdapter(null); Toast.makeText(this, "Geen armaturen voor: "+locatie, Toast.LENGTH_SHORT).show(); }

        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_LAST_LOCATIE, locatie).apply();
    }

    private void showRowMenu(final int inspectieid, final String fixtureCode) {
        CharSequence[] items = new CharSequence[]{"Gebreken bekijken","Gebrek toevoegen","Inspectie-ID kopiëren"};
        new AlertDialog.Builder(this)
                .setTitle(fixtureCode!=null && !fixtureCode.isEmpty()? fixtureCode : ("Inspectie #"+inspectieid))
                .setItems(items, (dialog, which) -> {
                    if (which==0) startDefectList(inspectieid, fixtureCode);
                    else if (which==1) startAddDefect(inspectieid, fixtureCode);
                    else {
                        ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm!=null) cm.setPrimaryClip(ClipData.newPlainText("inspectie_id", String.valueOf(inspectieid)));
                        Toast.makeText(this, "Inspectie-ID gekopieerd", Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private void startAddDefect(int inspectieid, String fixtureCode) {
        Intent i = new Intent(this, AddDefectActivity.class);
        i.putExtra(AddDefectActivity.EXTRA_INSPECTIE_ID, inspectieid);
        i.putExtra(AddDefectActivity.EXTRA_TITEL, (fixtureCode!=null && !fixtureCode.isEmpty())? ("Armatuur: "+fixtureCode): ("Armatuur #"+inspectieid));
        String locatie = getIntent()!=null? getIntent().getStringExtra(LocationListActivity.EXTRA_LOCATIE): null; if (locatie==null) locatie = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_LAST_LOCATIE, null);
        if (locatie!=null) i.putExtra(LocationListActivity.EXTRA_LOCATIE, locatie);
        startActivity(i);
    }

    private void startDefectList(int inspectieid, String fixtureCode) {
        Intent i = new Intent(this, DefectListActivity.class);
        i.putExtra(DefectListActivity.EXTRA_INSPECTIE_ID, inspectieid);
        i.putExtra(DefectListActivity.EXTRA_TITEL, (fixtureCode!=null? ("Gebreken - "+fixtureCode): ("Gebreken #"+inspectieid)));
        String locatie = getIntent()!=null? getIntent().getStringExtra(LocationListActivity.EXTRA_LOCATIE): null; if (locatie==null) locatie = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_LAST_LOCATIE, null);
        if (locatie!=null) i.putExtra(LocationListActivity.EXTRA_LOCATIE, locatie);
        startActivity(i);
    }

    private void reload() {
        String locatie = getIntent()!=null? getIntent().getStringExtra(LocationListActivity.EXTRA_LOCATIE): null;
        if (locatie==null || locatie.trim().isEmpty()) locatie = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_LAST_LOCATIE, null);
        if (locatie!=null) { if (getSupportActionBar()!=null) getSupportActionBar().setTitle(locatie); load(locatie); }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) { finish(); return true; }

        if (id == R.id.action_columns) {
            startActivity(new Intent(this, ColumnSettingsActivity.class));
            return true;
        }

        if (id == R.id.action_defects) {
            // Toggle checkmark
            boolean newState = !item.isChecked();
            item.setChecked(newState);

            // Update state + UI
            showDefects = newState;
            Map<String,Integer> colW = computeColumnWidthsPx();
            List<ColumnConfig> cfg = ColumnConfigManager.load(this);
            buildHeader(cfg, colW, showDefects);
            if (adapter != null) adapter.setShowDefects(showDefects);

            return true;
        }

        return super.onOptionsItemSelected(item);
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_fixture_list, menu);

        // Zorg dat de check-state in sync is met showDefects
        MenuItem m = menu.findItem(R.id.action_defects);
        if (m != null) m.setChecked(showDefects);

        return true;
    }
    @Override protected void onDestroy() { super.onDestroy(); if (cursor!=null && !cursor.isClosed()) cursor.close(); }

    // === Helpers ===
    private boolean hasDefects(int inspectieid) {
        // Cache-hit?
        Boolean cached = defectFlagCache.get(inspectieid);
        if (cached != null) return cached;

        // Gebruik jouw DefectProvider: lege string == geen gebreken
        String summary = (defectProvider != null) ? defectProvider.summaryFor(inspectieid) : null;
        boolean has = (summary != null && !summary.trim().isEmpty());

        defectFlagCache.put(inspectieid, has);
        return has;
    }
    /** Marker hit-test in view-coördinaten (px). */
    @Nullable
    private MarkerData hitTestMarker(float vx, float vy) {
        if (pdfView == null || currentPdf == null) return null;

        final float zoom = Math.max(1f, pdfView.getZoom());
        final float curX = pdfView.getCurrentXOffset(); // offsets!
        final float curY = pdfView.getCurrentYOffset();

        // View (px) -> Document space (ongezoomd)
        final float docX = (vx - curX) / zoom;
        final float docY = (vy - curY) / zoom;

        // Bepaal pagina (verticale stapeling)
        int pages = pdfView.getPageCount();
        float sumHeights = 0f;
        int pageIndex = -1;
        for (int i = 0; i < pages; i++) {
            float h = pdfView.getPageSize(i).getHeight();
            if (docY >= sumHeights && docY <= sumHeights + h) {
                pageIndex = i;
                break;
            }
            sumHeights += h;
        }
        if (pageIndex < 0) return null;

        final float pageW = pdfView.getPageSize(pageIndex).getWidth();
        final float pageH = pdfView.getPageSize(pageIndex).getHeight();

        List<MarkerData> lijst = markersByPdfPage.get(key(currentPdf.getName(), pageIndex));
        if (lijst == null || lijst.isEmpty()) return null;

        final float hitRadiusPx = dp(24);
        MarkerData best = null;
        float bestDist2 = Float.MAX_VALUE;

        for (MarkerData m : lijst) {
            float mxDoc = (float) (m.x * pageW);
            float myDoc = (float) (m.y * pageH + sumHeights);

            float mxView = curX + mxDoc * zoom;
            float myView = curY + myDoc * zoom;

            float dx = mxView - vx;
            float dy = myView - vy;
            float d2 = dx*dx + dy*dy;

            if (d2 < bestDist2) {
                bestDist2 = d2;
                best = m;
            }
        }

        return (best != null && bestDist2 <= hitRadiusPx * hitRadiusPx) ? best : null;
    }

    /** Marker selecteren → lijst highlighten → (optioneel) pagina wisselen + centreren → puls. */
    private void selectFromMarker(@Nullable MarkerData m) {
        if (m == null) return;

        // 1) State bijwerken
        geselecteerdeInspectieId = m.inspectieid;

        // 2) Lijst highlighten + naar zicht
        if (adapter != null && list != null) {
            adapter.setActivatedInspectieId(geselecteerdeInspectieId);
            int pos = findPositionByInspectieId(geselecteerdeInspectieId);
            if (pos != -1) {
                list.setItemChecked(pos, true);
                list.smoothScrollToPosition(pos);
            }
        }

        // 3) Naar pagina springen en centreren (als nodig)
        if (pdfView != null) {
            if (pdfView.getCurrentPage() != m.pageIndex) {
                pdfView.jumpTo(m.pageIndex, /*animate*/ false);
            }
            final MarkerData mm = m;
            pdfView.post(() -> {
                ensureMinZoomForCenter(3.0f);
                centerOnMarkerDocSpace(mm);
            });
        }

        // 4) Puls starten en overlay redrawen
        startMarkerPulse();
        if (pdfView != null) pdfView.postInvalidateOnAnimation();
    }

    private int findPositionByInspectieId(int id) {
        if (adapter == null) return -1;
        Cursor cur = adapter.getCursor();
        if (cur == null) return -1;
        int idx = cur.getColumnIndex("inspectieid");
        if (idx < 0) return -1;
        for (cur.moveToFirst(); !cur.isAfterLast(); cur.moveToNext()) {
            if (cur.getInt(idx) == id) return cur.getPosition();
        }
        return -1;
    }

    private static Set<String> getColumns(SQLiteDatabase db, String table) { HashSet<String> out=new HashSet<>(); Cursor c=db.rawQuery("PRAGMA table_info("+table+")", null); try { int idx=c.getColumnIndex("name"); while (c.moveToNext()) out.add(c.getString(idx)); } finally { if (c!=null) c.close(); } return out; }
    private static String firstExisting(Set<String> cols, String... candidates){ for (String cand: candidates) for (String existing: cols) if (existing!=null && existing.equalsIgnoreCase(cand)) return existing; return null; }
    private static String q(String ident){ return "`"+ident+"`"; }
    private static String sel(String sourceCol, String alias){ return (sourceCol!=null)? (q(sourceCol)+" AS "+alias) : ("NULL AS "+alias); }

    private int widthFor(String alias){ if ("nr".equals(alias)) return 72; if ("code".equals(alias)) return 128; if ("soort".equals(alias)) return 96; if ("verdieping".equals(alias)) return 110; if ("op_tekening".equals(alias)) return 130; if ("type".equals(alias)) return 96; if ("merk".equals(alias)) return 128; if ("montagewijze".equals(alias)) return 160; if ("pictogram".equals(alias)) return 120; if ("accutype".equals(alias)) return 120; if ("artikelnr".equals(alias)) return 120; if ("accu_leeftijd".equals(alias)) return 140; if ("ats".equals(alias)) return 80; if ("duurtest".equals(alias)) return 110; if ("opmerking".equals(alias)) return 220; if ("gebreken".equals(alias)) return 200; return 120; }
    private int dp(int v){ return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }
    private int px(int vDp){ return dp(vDp); }

    private Map<String,Integer> computeColumnWidthsPx(){ HashMap<String,Integer> m=new HashMap<>(); String[] aliases = new String[]{"nr","code","soort","verdieping","op_tekening","type","merk","montagewijze","pictogram","accutype","artikelnr","accu_leeftijd","ats","duurtest","opmerking","gebreken"}; for (String a: aliases) m.put(a, px(widthFor(a))); return m; }

    private void buildHeader(List<ColumnConfig> cfg, Map<String,Integer> colWidthsPx, boolean withDefects){
        if (headerView==null){ headerView=findViewById(R.id.hscrollHeader);}
        SyncHorizontalScrollView hsv = headerView.findViewById(R.id.hscrollHeader);
        if (hsv!=null) hsv.setSyncer(syncManager);
        LinearLayout container = headerView.findViewById(R.id.headerContainer);
        if (container!=null){
            container.removeAllViews();
            for (ColumnConfig cc: cfg){
                if (!cc.visible) continue;
                TextView tv=new TextView(this);
                tv.setText(cc.label);
                tv.setTypeface(Typeface.DEFAULT_BOLD);
                tv.setTextColor(0xff222222);
                tv.setSingleLine(true);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                tv.setPadding(dp(0),dp(0),dp(12),dp(0));
                Integer w=colWidthsPx!=null? colWidthsPx.get(cc.alias): null; int widthPx=(w!=null? w: px(120));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(widthPx, LinearLayout.LayoutParams.WRAP_CONTENT);
                tv.setLayoutParams(lp);
                tv.setMinWidth(widthPx);
                tv.setMaxWidth(widthPx);
                container.addView(tv);
            }
            if (withDefects){
                TextView tv=new TextView(this);
                tv.setText("Gebreken");
                tv.setTypeface(Typeface.DEFAULT_BOLD);
                tv.setTextColor(0xff222222);
                tv.setSingleLine(true);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                tv.setPadding(dp(0),dp(0),dp(12),dp(0));
                Integer w=colWidthsPx!=null? colWidthsPx.get("gebreken"): null; int widthPx=(w!=null? w: px(200));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(widthPx, LinearLayout.LayoutParams.WRAP_CONTENT);
                tv.setLayoutParams(lp);
                tv.setMinWidth(widthPx);
                tv.setMaxWidth(widthPx);
                container.addView(tv);
            }
        }
        if (hsv!=null){
            final int x = syncManager.getCurrentX();
            hsv.post(() -> hsv.syncTo(x));
        }
    }

    // -- VERVANG DEZE INNER CLASS --
    // PATCH: DefectProvider -> leest nu uit field.db (DBField) i.p.v. inspecties.db
    static class DefectProvider {
        private final android.content.Context ctx;
        private final LruCache<Integer, String> cache = new LruCache<>(256);

        DefectProvider(android.content.Context ctx) {
            this.ctx = ctx.getApplicationContext();
        }

        /** Levert een korte samenvatting; lege string = geen gebreken. Bron: field.db (DBField). */
        String summaryFor(int inspectieid) {
            String v = cache.get(inspectieid);
            if (v != null) return v;

            SQLiteDatabase db = null;
            Cursor d = null;
            try {
                db = nl.mikekemmink.noodverlichting.noodverlichting.data.DBField
                        .getInstance(ctx)
                        .getReadableDatabase();

                // Probeer uitgebreid met GROUP_CONCAT
                try {
                    d = db.rawQuery(
                            "SELECT COUNT(*) AS cnt, GROUP_CONCAT(omschrijving, ' \n ') AS oms " +
                                    "FROM gebreken WHERE inspectie_id = ?",
                            new String[]{ String.valueOf(inspectieid) }
                    );
                    if (d.moveToFirst()) {
                        int cnt = d.getInt(d.getColumnIndexOrThrow("cnt"));
                        String oms = null;
                        int idx = d.getColumnIndex("oms");
                        if (idx >= 0) oms = d.getString(idx);
                        v = (cnt <= 0) ? "" : (cnt + "× " + (oms != null ? oms : "gebrek"));
                    }
                } catch (Exception e) {
                    // Fallback zonder GROUP_CONCAT
                    if (d != null) { d.close(); d = null; }
                    d = db.rawQuery(
                            "SELECT COUNT(*) AS cnt FROM gebreken WHERE inspectie_id = ?",
                            new String[]{ String.valueOf(inspectieid) }
                    );
                    if (d.moveToFirst()) {
                        int cnt = d.getInt(d.getColumnIndexOrThrow("cnt"));
                        v = (cnt <= 0) ? "" : (cnt + "× gebrek(en)");
                    }
                }
            } catch (Exception ignore) {
                v = "";
            } finally {
                if (d != null) d.close();
            }

            if (v == null) v = "";
            cache.put(inspectieid, v);
            return v;
        }

        /** Maakt de cache leeg zodat kleuren/samenvattingen weer kloppen na wijzigingen. */
        void invalidate() {
            cache.evictAll();
        }
    } // <— LET OP: deze sluit alleen de DefectProvider class


    private static long optLongAny(JSONObject o, String[] keys, long def){ for(String k: keys) if (o.has(k)){ try { return o.getLong(k);} catch(Exception ignore){} try { return (long) o.getDouble(k);} catch(Exception ignore){} try { return Long.parseLong(o.getString(k));} catch(Exception ignore){} } return def; }
    private static int optIntAny(JSONObject o, String[] keys, int def){ for(String k: keys) if (o.has(k)){ try { return o.getInt(k);} catch(Exception ignore){} try { return (int) o.getDouble(k);} catch(Exception ignore){} try { return Integer.parseInt(o.getString(k));} catch(Exception ignore){} } return def; }
    private static double optDoubleAny(JSONObject o, String[] keys, double def){ for(String k: keys) if (o.has(k)){ try { return o.getDouble(k);} catch(Exception ignore){} try { return Double.parseDouble(o.getString(k));} catch(Exception ignore){} try { return o.getInt(k);} catch(Exception ignore){} } return def; }
    private static String optStringAny(JSONObject o, String[] keys, String def){ for(String k: keys) if (o.has(k)){ String v=o.optString(k, def); if (v!=null) return v;} return def; }

    static class MarkerData { String pdfNaam; double x,y; int pageIndex; int inspectieid; MarkerData(String pdfNaam, double x, double y, int pageIndex, int inspectieid){ this.pdfNaam=pdfNaam; this.x=x; this.y=y; this.pageIndex=pageIndex; this.inspectieid=inspectieid; } }
}
