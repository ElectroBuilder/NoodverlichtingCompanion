package nl.mikekemmink.noodverlichting.noodverlichting.sync.model;

public class ChangeDto {
    public long id;
    public String ts;
    public String table; // "gebreken" | "armatuur_posities"
    public String op;    // INSERT | UPDATE | DELETE
    public long row_id;
    public String payload; // JSON string; parse per table
}