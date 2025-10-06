
package nl.mikekemmink.noodverlichting.noodverlichting;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;

public class SyncHorizontalScrollView extends HorizontalScrollView {

    private ScrollSyncManager manager;
    private boolean suppress = false;

    public SyncHorizontalScrollView(Context context) { super(context); init(); }
    public SyncHorizontalScrollView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public SyncHorizontalScrollView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        setHorizontalScrollBarEnabled(false); // optioneel
        setFillViewport(true);                // child vult de breedte
        setOverScrollMode(OVER_SCROLL_NEVER); // optioneel
    }

    /** Koppel of wissel de sync-manager. */
    public void setSyncer(ScrollSyncManager m) {
        if (manager == m) return;
        if (manager != null) manager.unregister(this);
        manager = m;
        if (manager != null) manager.register(this);
    }

    /** Wordt door manager aangeroepen voor programmatic sync. */
    void syncTo(int x) {
        if (getScrollX() == x) return;
        suppress = true;
        scrollTo(x, 0);
        suppress = false;
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (!suppress && manager != null) manager.onClientScrolled(this, l);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Belangrijk: bij her-gebruik van ListView-rows worden views weer attached.
        // Registreer opnieuw en zet direct naar currentX om sync te behouden na verticaal scrollen.
        if (manager != null) {
            manager.register(this);
            final int x = manager.getCurrentX();
            post(() -> syncTo(x));
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (manager != null) manager.unregister(this);
        super.onDetachedFromWindow();
    }

    /** Veilig verwijderen uit oude parent (optioneel). */
    public static void safeDetach(android.view.View v) {
        if (v == null) return;
        android.view.ViewParent p = v.getParent();
        if (p instanceof ViewGroup) ((ViewGroup) p).removeView(v);
    }
}
