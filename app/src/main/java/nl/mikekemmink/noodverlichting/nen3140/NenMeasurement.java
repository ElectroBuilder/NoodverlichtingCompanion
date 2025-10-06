package nl.mikekemmink.noodverlichting.nen3140;

public class NenMeasurement {
    public String id;
    public double L1, L2, L3, N, PE;
    public long timestamp;

    public NenMeasurement() {
        // Lege constructor voor (de)serialisatie indien nodig
    }

    // ✅ Primair: 7-arg constructor met id
    public NenMeasurement(String id, double l1, double l2, double l3, double n, double pe, long ts) {
        // Als id null/empty is, maken we er zelf één — handig bij add()
        this.id = (id == null || id.isEmpty())
                ? java.util.UUID.randomUUID().toString()
                : id;
        this.L1 = l1;
        this.L2 = l2;
        this.L3 = l3;
        this.N  = n;
        this.PE = pe;
        this.timestamp = ts;
    }

    // ✅ Convenience: 6-arg constructor (backwards compat)
    public NenMeasurement(double l1, double l2, double l3, double n, double pe, long ts) {
        this(null, l1, l2, l3, n, pe, ts);
    }
}