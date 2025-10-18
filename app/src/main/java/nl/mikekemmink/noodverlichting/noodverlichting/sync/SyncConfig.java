package nl.mikekemmink.noodverlichting.noodverlichting.sync;

import android.content.Context;
import android.content.SharedPreferences;

public class SyncConfig {
    private static final String PREFS = "sync_prefs";
    private static final String KEY_HOST = "host";
    private static final String KEY_PORT = "port";
    private static final String KEY_API = "apiKey";
    private static final String KEY_LAST_SYNC = "last_sync_utc";

    public static void setServer(Context c, String host, int port, String apiKey) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        p.edit().putString(KEY_HOST, host)
                .putInt(KEY_PORT, port)
                .putString(KEY_API, apiKey)
                .apply();
    }

    public static String host(Context c){ return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_HOST, "192.168.2.34"); }
    public static int port(Context c){ return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_PORT, 8765); }
    public static String apiKey(Context c){ return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_API, "mijn-super-key"); }

    public static String lastSync(Context c){ return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST_SYNC, "1970-01-01T00:00:00Z"); }
    public static void setLastSync(Context c, String isoUtc){
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LAST_SYNC, isoUtc).apply();
    }
}