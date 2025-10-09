
package nl.mikekemmink.noodverlichting.nen3140;

import androidx.annotation.Nullable;
import org.json.JSONObject;

public class NenMeasurement {
    public String id;

    // Stroom-waarden nu nullable
    @Nullable public Double L1, L2, L3, N, PE;

    public long timestamp;

    // SPD (V) blijft nullable
    @Nullable public Double spdL1, spdL2, spdL3, spdN;

    public NenMeasurement() { }

    public NenMeasurement(String id,
                          @Nullable Double L1,
                          @Nullable Double L2,
                          @Nullable Double L3,
                          @Nullable Double N,
                          @Nullable Double PE,
                          long ts) {
        this.id = id;
        this.L1 = L1;
        this.L2 = L2;
        this.L3 = L3;
        this.N = N;
        this.PE = PE;
        this.timestamp = ts;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            if (id != null) o.put("id", id);

            // Schrijf alleen keys weg als de waarde niet null is
            if (L1 != null) o.put("L1", L1);
            if (L2 != null) o.put("L2", L2);
            if (L3 != null) o.put("L3", L3);
            if (N  != null) o.put("N",  N);
            if (PE != null) o.put("PE", PE);

            o.put("timestamp", timestamp);

            if (spdL1 != null) o.put("spdL1", spdL1);
            if (spdL2 != null) o.put("spdL2", spdL2);
            if (spdL3 != null) o.put("spdL3", spdL3);
            if (spdN != null) o.put("spdN", spdN);
        } catch (Exception ignore) { }
        return o;
    }

    public static NenMeasurement fromJson(JSONObject o) {
        NenMeasurement m = new NenMeasurement();
        m.id = o.optString("id", null);

        // Alleen zetten als de key echt bestaat; anders null laten
        m.L1 = o.has("L1") ? o.optDouble("L1") : null;
        m.L2 = o.has("L2") ? o.optDouble("L2") : null;
        m.L3 = o.has("L3") ? o.optDouble("L3") : null;
        m.N  = o.has("N")  ? o.optDouble("N")  : null;
        m.PE = o.has("PE") ? o.optDouble("PE") : null;

        m.timestamp = o.optLong("timestamp", System.currentTimeMillis());

        m.spdL1 = o.has("spdL1") ? o.optDouble("spdL1") : null;
        m.spdL2 = o.has("spdL2") ? o.optDouble("spdL2") : null;
        m.spdL3 = o.has("spdL3") ? o.optDouble("spdL3") : null;
        m.spdN = o.has("spdN") ? o.optDouble("spdN") : null;

        return m;
    }
}
