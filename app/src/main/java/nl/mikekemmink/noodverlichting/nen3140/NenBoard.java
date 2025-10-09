package nl.mikekemmink.noodverlichting.nen3140;

import org.json.JSONObject;
import androidx.annotation.Nullable;

public class NenBoard {
    private String id;
    private String name;

    // Board-level photos (verdeelkast + info)
    @Nullable private String photoBoardPath;
    @Nullable private String photoInfoPath;

    public NenBoard(String id, String name) {
        this.id = id; this.name = name;
    }
    public NenBoard() {}

    public String getId() { return id; }
    public String getName() { return name; }
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }

    public @Nullable String getPhotoBoardPath(){ return photoBoardPath; }
    public @Nullable String getPhotoInfoPath(){ return photoInfoPath; }
    public void setPhotoBoardPath(@Nullable String p){ this.photoBoardPath = p; }
    public void setPhotoInfoPath(@Nullable String p){ this.photoInfoPath = p; }

    public JSONObject toJson(){
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("name", name);
            if (photoBoardPath != null) o.put("photoBoardPath", photoBoardPath);
            if (photoInfoPath  != null) o.put("photoInfoPath",  photoInfoPath);
        } catch (Exception ignore) {}
        return o;
    }
    public static NenBoard fromJson(JSONObject o){
        NenBoard b = new NenBoard();
        b.id = o.optString("id", null);
        b.name = o.optString("name", "");
        b.photoBoardPath = o.optString("photoBoardPath", null);
        b.photoInfoPath  = o.optString("photoInfoPath",  null);
        return b;
    }
}
