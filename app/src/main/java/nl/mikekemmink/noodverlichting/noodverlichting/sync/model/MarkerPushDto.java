package nl.mikekemmink.noodverlichting.noodverlichting.sync.model;
import java.util.List;

public class MarkerPushDto {
    public List<Item> markers;
    public static class Item {
        public int inspectie_id;
        public String pdf_naam;  // "X.pdf" (zonder #p)
        public Integer page;     // 1-based
        public double x;         // 0..1
        public double y;         // 0..1
        public String op;        // "UPSERT" | "DELETE"
    }
}