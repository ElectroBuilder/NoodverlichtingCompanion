package nl.mikekemmink.noodverlichting.nen3140;

import org.json.JSONObject;

public class NenLocation {
    private String id;
    private String name;

    public NenLocation() {}
    public NenLocation(String id, String name){ this.id = id; this.name = name; }

    public String getId(){ return id; }
    public String getName(){ return name; }

    public JSONObject toJson(){
        JSONObject o = new JSONObject();
        try { o.put("id", id); o.put("name", name); } catch (Exception ignore) {}
        return o;
    }

    public static NenLocation fromJson(JSONObject o){
        return new NenLocation(o.optString("id", null), o.optString("name", ""));
    }
}
