package nl.mikekemmink.noodverlichting.noodverlichting.sync;

import android.content.Context;
import com.google.gson.Gson;
import okhttp3.*;
import java.io.File;
import java.util.concurrent.TimeUnit;

public class SyncClient {
    private final OkHttpClient http;
    private final Gson gson = new Gson();
    private final Context ctx;

    public SyncClient(Context ctx){
        this.ctx = ctx.getApplicationContext();
        this.http = new OkHttpClient.Builder()
                .callTimeout(30, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    Request r = chain.request().newBuilder()
                            .addHeader("X-API-Key", SyncConfig.apiKey(this.ctx))
                            .build();
                    return chain.proceed(r);
                })
                .build();
    }

    private String base(){ return "http://" + SyncConfig.host(ctx) + ":" + SyncConfig.port(ctx); }

    public String ping() throws Exception {
        Request req = new Request.Builder().url(base()+"/ping").get().build();
        try(Response resp = http.newCall(req).execute()){
            if(!resp.isSuccessful()) throw new Exception("Ping failed: " + resp.code());
            return resp.body()!=null? resp.body().string() : "";
        }
    }

    public String pullSince(String isoUtc) throws Exception {
        HttpUrl url = HttpUrl.parse(base()+"/pull").newBuilder()
                .addQueryParameter("since", isoUtc)
                .addQueryParameter("limit", "1000").build();
        Request req = new Request.Builder().url(url).get().build();
        try(Response resp = http.newCall(req).execute()){
            if(!resp.isSuccessful()) throw new Exception("Pull failed: " + resp.code());
            return resp.body()!=null? resp.body().string() : "{}";
        }
    }

    public String pushDefects(String jsonEnvelope) throws Exception {
        RequestBody body = RequestBody.create(jsonEnvelope, MediaType.get("application/json; charset=utf-8"));
        Request req = new Request.Builder().url(base()+"/push").post(body).build();
        try(Response resp = http.newCall(req).execute()){
            if(!resp.isSuccessful()) throw new Exception("Push defects failed: " + resp.code());
            return resp.body()!=null? resp.body().string() : "{}";
        }
    }

    /** NEW: send plain markers array (like markers.json) to /push_markers_json */
    public String pushMarkersJson(String jsonArray) throws Exception {
        RequestBody body = RequestBody.create(jsonArray, MediaType.get("application/json; charset=utf-8"));
        Request req = new Request.Builder().url(base()+"/push_markers_json").post(body).build();
        try(Response resp = http.newCall(req).execute()){
            if(!resp.isSuccessful()) throw new Exception("push_markers_json failed: " + resp.code());
            return resp.body()!=null? resp.body().string() : "{}";
        }
    }

    /** Old (kept for backward compat, not used) */
    public String pushMarkers(String jsonEnvelope) throws Exception {
        RequestBody body = RequestBody.create(jsonEnvelope, MediaType.get("application/json; charset=utf-8"));
        Request req = new Request.Builder().url(base()+"/push_markers").post(body).build();
        try(Response resp = http.newCall(req).execute()){
            if(!resp.isSuccessful()) throw new Exception("Push markers failed: " + resp.code());
            return resp.body()!=null? resp.body().string() : "{}";
        }
    }

    public String uploadPhoto(long gebrekId, File file) throws Exception {
        RequestBody fileBody = RequestBody.create(file, MediaType.parse("image/jpeg"));
        MultipartBody mp = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("gebrek_id", String.valueOf(gebrekId))
                .addFormDataPart("file", file.getName(), fileBody)
                .build();
        Request req = new Request.Builder().url(base()+"/upload").post(mp).build();
        try(Response resp = http.newCall(req).execute()){
            if(!resp.isSuccessful()) throw new Exception("Upload failed: " + resp.code());
            return resp.body()!=null? resp.body().string() : "{}";
        }
    }
}
