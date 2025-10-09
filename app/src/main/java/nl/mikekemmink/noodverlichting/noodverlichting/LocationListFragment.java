package nl.mikekemmink.noodverlichting.noodverlichting;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import nl.mikekemmink.noodverlichting.R;
import nl.mikekemmink.noodverlichting.ui.BaseToolbarActivity;

public class LocationListFragment extends Fragment
        implements BaseToolbarActivity.ToolbarActionSource {

    public LocationListFragment() {
        super(R.layout.nv_fragment_locaties); // jouw layout
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        BaseToolbarActivity a = (BaseToolbarActivity) requireActivity();
        a.setToolbarActions(this);
        if (a.getSupportActionBar() != null) {
            a.getSupportActionBar().setTitle(R.string.title_locations);
        }
        // TODO: init UI + data
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        BaseToolbarActivity a = (BaseToolbarActivity) requireActivity();
        a.setToolbarActions(null);
    }

    // ToolbarActionSource
    @Override public @MenuRes int getToolbarMenuRes() { return 0; } // begin zonder menu
    @Override public void onPrepareToolbarMenu(@NonNull Menu menu) {}
    @Override public boolean onToolbarItemSelected(@NonNull MenuItem item) { return false; }
}

