package nl.mikekemmink.noodverlichting.noodverlichting.sync.ui;

import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;

/**
 * R-onafhankelijke helper: geef resource-IDs vanuit je Activity door.
 */
public class SyncMenuHelper {
    public static void onCreateOptionsMenu(Activity a, Menu menu, int menuResId){
        a.getMenuInflater().inflate(menuResId, menu);
    }
    public static boolean onOptionsItemSelected(Activity a, MenuItem item, int actionId){
        if(item.getItemId() == actionId){
            a.startActivity(new Intent(a, SyncStatusActivity.class));
            return true;
        }
        return false;
    }
}
