package nl.mikekemmink.noodverlichting.noodverlichting.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DBInspecties {
    public static SQLiteDatabase tryOpenReadOnly(Context ctx) {
        File db = ctx.getDatabasePath("inspecties.db");
        if (!db.exists()) return null;
        return SQLiteDatabase.openDatabase(db.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
    }

    public static List<String> getDistinctLocaties(SQLiteDatabase db) {
        List<String> out = new ArrayList<>();
        out.add("Alle locaties");
        String sql = "SELECT DISTINCT Locatie FROM inspecties WHERE Locatie IS NOT NULL AND TRIM(Locatie) <> '' ORDER BY Locatie";
        Cursor c = db.rawQuery(sql, null);
        while (c.moveToNext()) {
            out.add(c.getString(0));
        }
        c.close();
        return out;
    }
}