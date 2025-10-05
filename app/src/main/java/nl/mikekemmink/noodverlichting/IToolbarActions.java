
package nl.mikekemmink.noodverlichting;

public interface IToolbarActions {
    /**
     * Worden gebreken momenteel getoond in deze view?
     */
    boolean isDefectsShown();

    /**
     * Toggle vanuit de toolbar.
     */
    void onToggleDefects(boolean show);

    /**
     * Open de bestaande Kolommen-UI van dit scherm.
     */
    void onColumnsClicked();
}
