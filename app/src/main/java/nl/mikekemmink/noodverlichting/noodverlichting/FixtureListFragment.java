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

public class FixtureListFragment extends Fragment
        implements BaseToolbarActivity.ToolbarActionSource {

    private static final String STATE_SHOW_DEFECTS = "state_show_defects";
    private boolean showDefects = false;

    public FixtureListFragment() {
        // Koppel je layout (zie sectie 3 hieronder)
        super(R.layout.nv_fragment_armaturen);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            showDefects = savedInstanceState.getBoolean(STATE_SHOW_DEFECTS, false);
        }
        // Als je data wilt laden die config-changes overleeft, overweeg ViewModel.
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_SHOW_DEFECTS, showDefects);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        BaseToolbarActivity a = (BaseToolbarActivity) requireActivity();

        // Registreer dit fragment als bron voor toolbar-acties
        a.setToolbarActions(this);

        // (Optioneel) zet de titel en het kleurpalet van de toolbar
        if (a.getSupportActionBar() != null) {
            a.getSupportActionBar().setTitle(R.string.title_fixtures);
        }
        // applyPalette is 'protected' in dezelfde package => kan direct aangeroepen worden
        a.applyPalette(BaseToolbarActivity.Palette.NOOD);

        // TODO: hier je RecyclerView/Adapter initialiseren en data inladen
        // RecyclerView rv = view.findViewById(R.id.rvFixtures);
        // rv.setAdapter(...); rv.setLayoutManager(...);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Opruimen zodat een volgend fragment z'n eigen menu kan tonen
        BaseToolbarActivity a = (BaseToolbarActivity) requireActivity();
        a.setToolbarActions(null);
    }

    // ===== ToolbarActionSource implementatie =====

    @Override
    @MenuRes
    public int getToolbarMenuRes() {
        // Start veilig zonder menu (0) om inflating-crashes te vermijden.
        // Zet dit naar R.menu.menu_fixture_list zodra je sectie 2 toevoegt.
        // return 0;
        return R.menu.menu_fixture_list;
    }

}