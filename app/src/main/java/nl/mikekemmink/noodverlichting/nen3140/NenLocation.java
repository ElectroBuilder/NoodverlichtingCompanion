package nl.mikekemmink.noodverlichting.nen3140;

import java.util.UUID;

public class NenLocation {
    private String id;
    private String name;

    public NenLocation(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
    }
    public NenLocation(String id, String name) { this.id = id; this.name = name; }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String n) { this.name = n; }
}
