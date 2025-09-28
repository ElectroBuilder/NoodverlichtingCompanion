package nl.mikekemmink.noodverlichting.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DBField extends SQLiteOpenHelper {
    private static final String NAME = "field.db";
    private static final int VERSION = 1;
    private static DBField instance;

    public static synchronized DBField getInstance(Context ctx) {
        if (instance == null) instance = new DBField(ctx.getApplicationContext());
        return instance;
    }

    private DBField(Context ctx) { super(ctx, NAME, null, VERSION); }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS gebreken ("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT, "+
                "inspectie_id INTEGER NOT NULL, "+
                "omschrijving TEXT NOT NULL, "+
                "datum TEXT, "+
                "foto_pad TEXT) ");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }
}