package nl.mikekemmink.noodverlichting.nen3140;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NenBoard {
    private String id;
    private String name;
    private List<NenMeasurement> measurements = new ArrayList<>();

    public NenBoard(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public List<NenMeasurement> getMeasurements() { return measurements; }
}
