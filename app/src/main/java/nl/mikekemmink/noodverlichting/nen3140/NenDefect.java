
package nl.mikekemmink.noodverlichting.nen3140;

public class NenDefect {
    public String id;
    public String text;
    public String photo; // bestandsnaam onder files/nen3140/photos/
    public long timestamp;

    public NenDefect(String id, String text, String photo, long timestamp) {
        this.id = id;
        this.text = text;
        this.photo = photo;
        this.timestamp = timestamp;
    }
}
