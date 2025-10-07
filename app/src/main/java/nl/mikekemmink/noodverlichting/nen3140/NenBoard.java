
package nl.mikekemmink.noodverlichting.nen3140;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import androidx.annotation.Nullable;

public class NenBoard {
    private String id;
    private String name;
    private List<NenMeasurement> measurements = new ArrayList<>();

    // --- Meerdere gebreken ---
    private List<NenDefect> defects = new ArrayList<>();

    public NenBoard(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public List<NenMeasurement> getMeasurements() { return measurements; }
    public List<NenDefect> getDefects() { return defects; }

    public boolean hasDefects() { return defects != null && !defects.isEmpty(); }
    public int getDefectsCount() { return defects == null ? 0 : defects.size(); }

    // --- Laatste meting helpers ---
    @Nullable
    public NenMeasurement getLatestMeasurement() {
        if (measurements == null || measurements.isEmpty()) return null;
        NenMeasurement latest = measurements.get(0);
        for (NenMeasurement m : measurements) {
            if (m.timestamp > latest.timestamp) latest = m;
        }
        return latest;
    }

    /** Compacte weergave voor in het overzicht; voorbeeld: "L1 12.3  L2 12.1  L3 12.0  N 0.0  PE 0.1 A" */
    public String formatLatestValues() {
        NenMeasurement m = getLatestMeasurement();
        if (m == null) return "— geen metingen —";
        java.util.Locale loc = java.util.Locale.getDefault();
        return String.format(loc,
                "L1 %.1f  L2 %.1f  L3 %.1f  N %.1f  PE %.1f A",
                m.L1, m.L2, m.L3, m.N, m.PE);
    }

    /** Datum/tijd van laatste meting, of lege string. */
    public String formatLatestTimestamp() {
        NenMeasurement m = getLatestMeasurement();
        if (m == null || m.timestamp <= 0) return "";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd-MM-yyyy HH:mm", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(m.timestamp));
    }
}
