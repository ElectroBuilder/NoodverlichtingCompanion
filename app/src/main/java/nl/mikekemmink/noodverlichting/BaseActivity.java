
package nl.mikekemmink.noodverlichting;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

public class BaseActivity extends AppCompatActivity {

    private IToolbarActions toolbarActions; // geleverd door huidige Fragment/scherm
    private MaterialToolbar toolbar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Let op: jouw Activity layout moet een Toolbar met id 'toolbar' bevatten.
        // Voorbeeldlayout staat in res/layout/activity_with_toolbar.xml
    }

    public void attachToolbar(MaterialToolbar tb) {
        this.toolbar = tb;
        setSupportActionBar(tb);
    }

    public void setToolbarActions(@Nullable IToolbarActions actions) {
        this.toolbarActions = actions;
        invalidateOptionsMenu();
    }
}
