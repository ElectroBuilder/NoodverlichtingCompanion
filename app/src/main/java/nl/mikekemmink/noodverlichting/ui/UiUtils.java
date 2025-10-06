
package nl.mikekemmink.noodverlichting.ui;

import android.app.Activity;
import android.view.Window;

import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class UiUtils {
    /**
     * Tint de statusbar-kleur en icon-tint.
     * @param activity Activity context
     * @param colorRes  Kleur resource voor status bar
     * @param lightIcons true = donkere iconen (alleen bij lichte achtergrond), false = witte iconen (bij donkere achtergrond)
     */
    public static void tintStatusBar(Activity activity, int colorRes, boolean lightIcons) {
        Window w = activity.getWindow();
        w.setStatusBarColor(ContextCompat.getColor(activity, colorRes));
        WindowInsetsControllerCompat ic = new WindowInsetsControllerCompat(w, w.getDecorView());
        ic.setAppearanceLightStatusBars(lightIcons);
    }
}
