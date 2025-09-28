
package nl.mikekemmink.noodverlichting.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import nl.mikekemmink.noodverlichting.BaseActivity;
import nl.mikekemmink.noodverlichting.IToolbarActions;
import nl.mikekemmink.noodverlichting.R;

public class LocationListFragment extends Fragment implements IToolbarActions {

    private boolean showDefects = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_location_list, container, false);
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof BaseActivity) {
            BaseActivity a = (BaseActivity) getActivity();
            // Zorg dat de Activity een toolbar heeft en registreer acties
            MaterialToolbar tb = a.findViewById(R.id.toolbar);
            if (tb != null) a.attachToolbar(tb);
            a.setToolbarActions(this);
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
        // TODO: Update je UI (bijv. kolom/sectie met gebreken tonen/verbergen)
    }

    @Override
    public void onColumnsClicked() {
        // TODO: Open jouw bestaande 'Kolommen' dialoog/BottomSheet voor locaties
    }
}
