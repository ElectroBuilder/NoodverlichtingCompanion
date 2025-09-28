package nl.mikekemmink.noodverlichting.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.File;

public class DBHelper extends SQLiteOpenHelper {

    private static final String TAG = "DBHelper";
    private static final String DB_NAME = "inspecties.db"; // << zo heb je 'm geïmporteerd
    // Versienummer is alleen voor SQLiteOpenHelper; we doen geen migraties
    private static final int DB_VERSION = 1;

    private final Context appContext;

    public DBHelper(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
        this.appContext = context.getApplicationContext();
        ensureDbExists();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Niets aanmaken: we gebruiken een bestaande database die je geïmporteerd hebt.
        // Als je ooit een lege DB wilt initialiseren, kun je hier CREATE TABLE's zetten.
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Geen migraties nodig voor de companion (read-only gebruik).
    }

    /**
     * Controle: staat het bestand echt waar SQLiteOpenHelper het verwacht?
     * Zo niet, log een nette melding. (De app kan dan een lege DB proberen
     * te maken; daarom is het handig om hier even te checken en te waarschuwen.)
     */
    private void ensureDbExists() {
        File dbFile = appContext.getDatabasePath(DB_NAME);
        if (!dbFile.exists()) {
            Log.w(TAG, "Databasebestand ontbreekt op: " + dbFile.getAbsolutePath()
                    + " (heb je 'm al geïmporteerd?)");
            // Optioneel: maak de map aan zodat je importkopie hierheen kan schrijven
            File parent = dbFile.getParentFile();
            if (parent != null && !parent.exists()) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
        } else {
            Log.d(TAG, "DB gevonden: " + dbFile.getAbsolutePath() + " (" + dbFile.length() + " bytes)");
        }
    }
}