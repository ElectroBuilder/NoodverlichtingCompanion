package nl.mikekemmink.noodverlichting.ui;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.graphics.drawable.Drawable;

import androidx.annotation.LayoutRes;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.appbar.MaterialToolbar;

import nl.mikekemmink.noodverlichting.R;

/**
 * Base activity met één gedeelde toolbar + statusbar-tint.
 * Schermen roepen setContentLayout(...) aan om hun content in te pluggen
 * en applyPalette(...) om groen/blauw te kiezen.
 */
public abstract class BaseToolbarActivity extends AppCompatActivity {

    // ====== Koppelpunt voor fragmenten (ToolbarActionSource) ======
    public interface ToolbarActionSource {
        /** Retourneer 0 als je (tijdelijk) geen menu wilt tonen. */
        @MenuRes int getToolbarMenuRes();
        /** Optioneel: menu-items dynamisch aanpassen (enable/visible). */
        default void onPrepareToolbarMenu(@NonNull Menu menu) {}
        /** Retourneer true als je het item afhandelt, anders false. */
        default boolean onToolbarItemSelected(@NonNull MenuItem item) { return false; }
    }

    @Nullable
    private ToolbarActionSource toolbarActionSource;

    /** Door fragments aan te roepen in onViewCreated(...) en opruimen in onDestroyView(). */
    public void setToolbarActions(@Nullable ToolbarActionSource source) {
        this.toolbarActionSource = source;
        invalidateOptionsMenu(); // menu opnieuw opbouwen
    }

    // ====== Compat: laat Activities ook zélf een menu leveren ======
    /** Override in je Activity als je een eigen menu wilt tonen. */
    protected @MenuRes int getActivityToolbarMenuRes() { return 0; }
    /** Laatste kans om Activity-menu dynamisch te tweaken (enable/visible). */
    protected void onPrepareActivityToolbarMenu(@NonNull Menu menu) {}
    /** Item-clicks uit Activity-menu afhandelen. Return true als verwerkt. */
    protected boolean onActivityToolbarItemSelected(@NonNull MenuItem item) { return false; }
    // ===============================================================

    protected MaterialToolbar toolbar;
    private int onColorRes = android.R.color.white; // menu/navigatie-icoon kleur
    private Palette currentPalette = Palette.NEUTRAL;

    public enum Palette { NOOD, NEN, NEUTRAL }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Zet de scaffold met 1 toolbar en content placeholder
        setContentView(R.layout.activity_scaffold);
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    /** Plaats je eigen content layout onder de toolbar. */
    protected void setContentLayout(@LayoutRes int layoutResId) {
        FrameLayout content = findViewById(R.id.content);
        LayoutInflater.from(this).inflate(layoutResId, content, true);
    }

    /** Toon/ verberg Up-knop. */
    protected void setUpEnabled(boolean enabled) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(enabled);
        }
        if (enabled) {
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { onBackPressed(); }
            });
        } else {
            toolbar.setNavigationOnClickListener(null);
        }
    }

    /** Pas kleuren van toolbar + statusbar aan per palette. */
    protected void applyPalette(Palette p) {
        currentPalette = p;
        int bgRes; int onRes; int statusRes;
        switch (p) {
            case NOOD:
                bgRes = R.color.nood_green; onRes = R.color.nood_on_green; statusRes = R.color.nood_green_status; break;
            case NEN:
                bgRes = R.color.nen_blue;  onRes = R.color.nen_on_blue;  statusRes = R.color.nen_blue_status;  break;
            default:
                // neutraal: gebruik theme surface
                bgRes = android.R.color.transparent; onRes = R.color.nen_on_blue; statusRes = android.R.color.black; break;
        }
        this.onColorRes = onRes;
        // Toolbar achtergr., titel en icon-tinten
        toolbar.setBackgroundColor(ContextCompat.getColor(this, bgRes));
        toolbar.setTitleTextColor(ContextCompat.getColor(this, onRes));
        toolbar.setSubtitleTextColor(ContextCompat.getColor(this, onRes));
        toolbar.setNavigationIconTint(ContextCompat.getColor(this, onRes));
        // Status bar (witte iconen bij donkere achtergrond => lightIcons=false)
        tintStatusBar(statusRes, /*lightIcons=*/ false);
        // Bestaande menu-iconen opnieuw tinten
        tintToolbarMenuIcons();
    }

    private void tintStatusBar(int colorRes, boolean lightIcons) {
        getWindow().setStatusBarColor(ContextCompat.getColor(this, colorRes));
        WindowInsetsControllerCompat ic = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        ic.setAppearanceLightStatusBars(lightIcons);
    }

    private void tintToolbarMenuIcons() {
        if (toolbar == null) return;
        toolbar.post(new Runnable() {
            @Override public void run() {
                if (toolbar.getMenu() == null) return;
                for (int i = 0; i < toolbar.getMenu().size(); i++) {
                    Drawable icon = toolbar.getMenu().getItem(i).getIcon();
                    if (icon != null) {
                        Drawable wrapped = DrawableCompat.wrap(icon.mutate());
                        DrawableCompat.setTint(wrapped, ContextCompat.getColor(BaseToolbarActivity.this, onColorRes));
                        toolbar.getMenu().getItem(i).setIcon(wrapped);
                    }
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean any = false;
        if (toolbarActionSource != null) {
            int res = toolbarActionSource.getToolbarMenuRes();
            if (res != 0) { getMenuInflater().inflate(res, menu); any = true; }
            toolbarActionSource.onPrepareToolbarMenu(menu);
            any = true;
        }
        // Compat: ook Activity-menu toestaan
        int actRes = getActivityToolbarMenuRes();
        if (actRes != 0) { getMenuInflater().inflate(actRes, menu); any = true; }
        onPrepareActivityToolbarMenu(menu);
        tintToolbarMenuIcons();
        return any || super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean handled = super.onPrepareOptionsMenu(menu);
        if (toolbarActionSource != null) { toolbarActionSource.onPrepareToolbarMenu(menu); handled = true; }
        onPrepareActivityToolbarMenu(menu);
        tintToolbarMenuIcons();
        return handled;
    }

    // Maak ze NIET-abstract met een veilige default:
    protected void onColumnsClicked() { /* no-op */ }
    protected boolean isDefectsShown() { return false; }
    protected void onToggleDefects(boolean show) { /* no-op */ }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Up/Home
        if (item.getItemId() == android.R.id.home) { onBackPressed(); return true; }
        // Eerst de bron (fragment) laten proberen
        if (toolbarActionSource != null && toolbarActionSource.onToolbarItemSelected(item)) return true;
        // Compat: Activity-menu handler
        if (onActivityToolbarItemSelected(item)) return true;
        return super.onOptionsItemSelected(item);
    }
}