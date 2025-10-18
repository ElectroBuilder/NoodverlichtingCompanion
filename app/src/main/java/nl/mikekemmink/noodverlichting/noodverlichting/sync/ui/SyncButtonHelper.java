package nl.mikekemmink.noodverlichting.noodverlichting.sync.ui;

import android.app.Activity;
import android.content.Intent;
import android.view.View;

/**
 * R-onafhankelijke helper: geef de ID van de knop door vanuit je Activity.
 */
public class SyncButtonHelper {
    public static void attach(final Activity a, int buttonId){
        View btn = a.findViewById(buttonId);
        if(btn != null){
            btn.setOnClickListener(v -> a.startActivity(new Intent(a, SyncStatusActivity.class)));
        }
    }
}
