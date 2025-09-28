package nl.mikekemmink.noodverlichting.columns;

public class ColumnConfig {
    public String alias;   // interne sleutel (bijv. "nr", "code", "soort")
    public String label;   // weergavenaam (bijv. "Nr.", "Code", "Soort")
    public boolean visible;

    public ColumnConfig() {}

    public ColumnConfig(String alias, String label, boolean visible) {
        this.alias = alias;
        this.label = label;
        this.visible = visible;
    }
}