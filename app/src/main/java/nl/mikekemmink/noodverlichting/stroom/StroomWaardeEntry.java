package nl.mikekemmink.noodverlichting.stroom;

import org.json.JSONException;
import org.json.JSONObject;
import nl.mikekemmink.noodverlichting.stroom.StroomRepo;
public class StroomWaardeEntry {
    public String id;        // UUID
    public String kastNaam;  // Key
    public double l1, l2, l3, n, pe;
    public long timestamp;   // System.currentTimeMillis()

    public StroomWaardeEntry(String id, String kastNaam,
                             double l1, double l2, double l3, double n, double pe,
                             long timestamp) {
        this.id = id;
        this.kastNaam = kastNaam;
        this.l1 = l1;
        this.l2 = l2;
        this.l3 = l3;
        this.n = n;
        this.pe = pe;
        this.timestamp = timestamp;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("kastNaam", kastNaam);
        o.put("l1", l1);
        o.put("l2", l2);
        o.put("l3", l3);
        o.put("n", n);
        o.put("pe", pe);
        o.put("timestamp", timestamp);
        return o;
    }

    public static StroomWaardeEntry fromJson(JSONObject o) throws JSONException {
        return new StroomWaardeEntry(
                o.optString("id", java.util.UUID.randomUUID().toString()),
                o.optString("kastNaam", ""),
                o.optDouble("l1", 0),
                o.optDouble("l2", 0),
                o.optDouble("l3", 0),
                o.optDouble("n", 0),
                o.optDouble("pe", 0),
                o.optLong("timestamp", System.currentTimeMillis())
        );
    }
}
