
package nl.mikekemmink.noodverlichting;

import android.app.Application;
import com.google.android.material.color.DynamicColors;

public class NoodverlichtingApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        // Past automatisch Material You (dynamic color) toe op Android 12+ (API 31)
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
