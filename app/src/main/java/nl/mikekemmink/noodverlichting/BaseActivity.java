
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main_toolbar, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean hasActions = toolbarActions != null;
        menu.setGroupVisible(R.id.group_main, hasActions);
        if (hasActions) {
            MenuItem defects = menu.findItem(R.id.action_toggle_defects);
            if (defects != null) defects.setChecked(toolbarActions.isDefectsShown());
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (toolbarActions == null) return super.onOptionsItemSelected(item);
        int id = item.getItemId();
        if (id == R.id.action_toggle_defects) {
            boolean newState = !item.isChecked();
            item.setChecked(newState);
            toolbarActions.onToggleDefects(newState);
            return true;
        } else if (id == R.id.action_columns) {
            toolbarActions.onColumnsClicked();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
