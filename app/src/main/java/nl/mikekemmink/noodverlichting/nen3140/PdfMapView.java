package nl.mikekemmink.noodverlichting.views;

import android.content.Context;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.util.AttributeSet;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.annotation.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


// imports bovenin:
import android.content.SharedPreferences;
import androidx.annotation.NonNull;


public class PdfMapView extends FrameLayout {
    private ZoomPanImageView imageView;
    private FrameLayout overlay;
    private PdfRenderer renderer;
    private PdfRenderer.Page currentPage;
    private int pageIndex = 0;
    private File pdfFile;
    private final List<ViMarker> markers = new ArrayList<>();
    // Memory cache voor gerenderde pagina’s (ongeveer 1/8 van het beschikbare geheugen)
    private static final LruCache<String, Bitmap> PAGE_MEM_CACHE = new LruCache<String, Bitmap>(
            (int) (Runtime.getRuntime().maxMemory() / 8)) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private static final String PREFS_NAME = "pdf_map_prefs";
    private static final String PREF_LAST_PDF_PREFIX = "last_pdf_";
    private static final String PREF_LAST_PAGE_PREFIX = "last_page_";

    @Nullable private String locationId; // per scherm in te stellen

    public static class ViMarker {
        public String viId;
        public int pageIndex;
        public float x; // 0..1
        public float y; // 0..1
    }
    public void setLocationId(@NonNull String locationId) {
        this.locationId = locationId;
    }


    public PdfMapView(Context c, AttributeSet a) {
        super(c, a);
        LayoutInflater.from(c).inflate(nl.mikekemmink.noodverlichting.R.layout.nen_plattegrond, this, true);
        imageView = findViewById(nl.mikekemmink.noodverlichting.R.id.ivPage);
        overlay   = findViewById(nl.mikekemmink.noodverlichting.R.id.overlay);
        imageView.setOnMatrixChanged(m -> layoutMarkers());
        setOnDragListener((v, event) -> onDragEventInternal(event));
    }

    private boolean onDragEventInternal(DragEvent event) {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return true;
            case DragEvent.ACTION_DROP:
                String viId = "";
                if (event.getClipData()!=null && event.getClipData().getItemCount()>0) {
                    CharSequence cs = event.getClipData().getItemAt(0).getText();
                    if (cs!=null) viId = cs.toString();
                }
                PointF p = viewToImagePoint(event.getX(), event.getY());
                if (p != null) {
                    float[] wh = getImageIntrinsicSize();
                    if (wh != null && wh[0] > 0 && wh[1] > 0) {
                        float nx = p.x / wh[0];
                        float ny = p.y / wh[1];
                        addMarker(viId, pageIndex, nx, ny, true);
                    }
                }
                return true;
        }
        return true;
    }

    public void openPdf(File file) {
        closeRenderer();
        this.pdfFile = file;
        try {
            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(pfd);

            // ⬇️ Herstel laatst bekeken pagina voor deze locatie (indien beschikbaar)
            pageIndex = getSavedPageForLocation();

            renderPage();            // jouw bestaande render
            loadMarkersFromDisk();   // jouw bestaande marker-load

            // ⬇️ Onthoud dat dit de "laatste PDF" is voor deze locatie
            persistLastPdfForLocation();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public int getCurrentPageIndex() {
        return pageIndex;
    }

    public int getPageCount() { return renderer != null ? renderer.getPageCount() : 0; }

    public void setPageIndex(int idx) {
        if (renderer == null) return;
        if (idx < 0 || idx >= renderer.getPageCount()) return;
        pageIndex = idx;
        renderPage();
        layoutMarkers();

        // ⬇️ pagina index onthouden per locatie
        persistLastPageForLocation();
    }

    private void renderPage() {
        if (renderer == null) return;

        // Open alleen als we echt moeten renderen of afmetingen nodig hebben
        if (currentPage != null) currentPage.close();
        currentPage = renderer.openPage(pageIndex);

        int w = currentPage.getWidth();
        int h = currentPage.getHeight();

        // 1) Memory cache proberen
        String key = buildCacheKey(pdfFile, pageIndex, w, h);
        Bitmap bmp = PAGE_MEM_CACHE.get(key);

        // 2) Disk cache proberen als memory miss
        if (bmp == null) {
            bmp = loadFromDisk(key);
            if (bmp != null) {
                PAGE_MEM_CACHE.put(key, bmp);
            }
        }

        // 3) Zo nodig renderen en daarna cachen
        if (bmp == null) {
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            canvas.drawColor(Color.WHITE);
            currentPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

            PAGE_MEM_CACHE.put(key, bmp);
            saveToDisk(key, bmp);
        }

        // 4) Tonen
        imageView.setImageBitmap(bmp);
        imageView.setImageMatrix(new Matrix());
        overlay.removeAllViews();
        addVisibleMarkers();
    }


    private void addVisibleMarkers() {
        for (ViMarker m : markers) {
            if (m.pageIndex == pageIndex) attachMarkerView(m);
        }
        layoutMarkers();
    }

    private void attachMarkerView(ViMarker m) {
        ImageView pin = new ImageView(getContext());
        pin.setImageResource(nl.mikekemmink.noodverlichting.R.drawable.ic_marker_pin);
        pin.setTag(m);
        int size = (int)(getResources().getDisplayMetrics().density * 28);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
        overlay.addView(pin, lp);

        pin.setOnLongClickListener(v -> {
            overlay.removeView(v);
            markers.remove(m);
            saveMarkersToDisk();
            return true;
        });
    }

    private void layoutMarkers() {
        float[] wh = getImageIntrinsicSize();
        if (wh == null) return;
        Matrix mat = imageView.getCurrentMatrix();
        for (int i=0; i<overlay.getChildCount(); i++) {
            View child = overlay.getChildAt(i);
            Object tag = child.getTag();
            if (!(tag instanceof ViMarker)) continue;
            ViMarker m = (ViMarker) tag;
            if (m.pageIndex != pageIndex) { child.setVisibility(GONE); continue; }
            float ix = m.x * wh[0];
            float iy = m.y * wh[1];
            float[] pt = new float[]{ix, iy};
            mat.mapPoints(pt);
            child.setX(pt[0] - child.getWidth()/2f);
            child.setY(pt[1] - child.getHeight()/2f);
            child.setVisibility(VISIBLE);
        }
    }

    private float[] getImageIntrinsicSize() {
        if (imageView.getDrawable() == null) return null;
        return new float[]{ imageView.getDrawable().getIntrinsicWidth(), imageView.getDrawable().getIntrinsicHeight() };
    }

    private @Nullable PointF viewToImagePoint(float vx, float vy) {
        if (imageView.getDrawable() == null) return null;
        Matrix inv = new Matrix();
        imageView.getCurrentMatrix().invert(inv);
        float[] pt = new float[]{vx, vy};
        inv.mapPoints(pt);
        return new PointF(pt[0], pt[1]);
    }

    public void addMarker(String viId, int pageIndex, float nx, float ny, boolean persist) {
        ViMarker m = new ViMarker();
        m.viId = viId; m.pageIndex = pageIndex; m.x = nx; m.y = ny;
        markers.add(m);
        if (m.pageIndex == this.pageIndex) attachMarkerView(m);
        layoutMarkers();
        if (persist) saveMarkersToDisk();
    }

    private void saveMarkersToDisk() {
        if (pdfFile == null) return;
        File json = new File(pdfFile.getParentFile(), pdfFile.getName() + ".markers.json");
        try (FileWriter fw = new FileWriter(json)) {
            JSONArray arr = new JSONArray();
            for (ViMarker m : markers) {
                JSONObject o = new JSONObject();
                o.put("viId", m.viId);
                o.put("pageIndex", m.pageIndex);
                o.put("x", m.x);
                o.put("y", m.y);
                arr.put(o);
            }
            fw.write(arr.toString());
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadMarkersFromDisk() {
        markers.clear();
        overlay.removeAllViews();
        if (pdfFile == null) return;
        File json = new File(pdfFile.getParentFile(), pdfFile.getName() + ".markers.json");
        if (!json.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(json))) {
            StringBuilder sb = new StringBuilder();
            String line; while ((line = br.readLine()) != null) sb.append(line);
            JSONArray arr = new JSONArray(sb.toString());
            for (int i=0;i<arr.length();i++) {
                JSONObject o = arr.getJSONObject(i);
                ViMarker m = new ViMarker();
                m.viId = o.getString("viId");
                m.pageIndex = o.getInt("pageIndex");
                m.x = (float)o.getDouble("x");
                m.y = (float)o.getDouble("y");
                markers.add(m);
            }
        } catch (Exception e) { e.printStackTrace(); }
        addVisibleMarkers();
    }
    private void persistLastPdfForLocation() {
        if (locationId == null || pdfFile == null) return;
        getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_LAST_PDF_PREFIX + locationId, pdfFile.getAbsolutePath())
                .apply();
    }

    private void persistLastPageForLocation() {
        if (locationId == null) return;
        getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(PREF_LAST_PAGE_PREFIX + locationId, pageIndex)
                .apply();
    }

    private int getSavedPageForLocation() {
        int idx = 0;
        if (locationId != null) {
            SharedPreferences sp = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            idx = sp.getInt(PREF_LAST_PAGE_PREFIX + locationId, 0);
        }
        if (renderer != null) {
            int max = Math.max(0, renderer.getPageCount() - 1);
            if (idx < 0 || idx > max) idx = 0;
        }
        return idx;
    }

    /** Probeer automatisch de laatst geopende PDF voor deze locatie te openen. */
    public boolean tryOpenLastPdfForLocation() {
        if (locationId == null) return false;
        SharedPreferences sp = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String path = sp.getString(PREF_LAST_PDF_PREFIX + locationId, null);
        if (path == null) return false;
        File f = new File(path);
        if (!f.exists()) return false;
        openPdf(f);
        return true;
    }

    private String buildCacheKey(File pdf, int page, int w, int h) {
        // Neem path + lastModified + pagina + afmetingen op in de key
        String raw = (pdf != null ? pdf.getAbsolutePath() : "null")
                + "|" + (pdf != null ? pdf.lastModified() : 0)
                + "|" + page + "|" + w + "x" + h;
        return md5(raw);
    }

    private static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] b = md.digest(s.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte value : b) sb.append(String.format("%02x", value));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private File getDiskCacheFile(String key) {
        File dir = new File(getContext().getCacheDir(), "pdf_pages");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, key + ".png");
    }

    private @Nullable Bitmap loadFromDisk(String key) {
        File f = getDiskCacheFile(key);
        if (!f.exists()) return null;
        return BitmapFactory.decodeFile(f.getAbsolutePath());
    }

    private void saveToDisk(String key, Bitmap bmp) {
        File f = getDiskCacheFile(key);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            // PNG is lossless; wil je iets kleiner, dan kun je WEBP_LOSSY/LOSSLESS gebruiken vanaf moderne API’s
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void closeRenderer() {
        try { if (currentPage != null) currentPage.close(); if (renderer != null) renderer.close(); } catch (Exception ignored) {}
        currentPage = null; renderer = null;
    }
}