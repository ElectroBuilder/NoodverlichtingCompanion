package nl.mikekemmink.noodverlichting.noodverlichting.sync.model;
import java.util.List;

public class DefectPushDto {
    public List<Item> defects;
    public static class Item {
        public int inspectie_id;
        public String omschrijving;
        public String datum; // "YYYY-MM-DD"
        public String client_temp_id; // optioneel
        public String client_photo_name; // optioneel
    }
}
