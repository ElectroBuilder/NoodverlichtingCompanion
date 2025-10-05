
package nl.mikekemmink.noodverlichting.ui;

import androidx.fragment.app.Fragment;

import nl.mikekemmink.noodverlichting.BaseActivity;
import nl.mikekemmink.noodverlichting.IToolbarActions;

public class FixtureListFragment extends Fragment implements IToolbarActions {

    private boolean showDefects = false;

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof BaseActivity) {
            BaseActivity a = (BaseActivity) getActivity();
            // Verwijder/laat weg: a.attachToolbar(tb);
            a.setToolbarActions(this); // Toolbar-items zichtbaar maken + state doorgeven
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getActivity() instanceof BaseActivity) {
            ((BaseActivity) getActivity()).setToolbarActions(null);
        }
    }

    // IToolbarActions
    @Override
    public boolean isDefectsShown() { return showDefects; }

    @Override
    public void onToggleDefects(boolean show) {
        this.showDefects = show;
        // TODO: Update je UI (bijv. highlight/filter armaturen met gebreken)
    }

    @Override
    public void onColumnsClicked() {
        // TODO: Open jouw bestaande 'Kolommen' dialoog/BottomSheet voor armaturen
    }
}
