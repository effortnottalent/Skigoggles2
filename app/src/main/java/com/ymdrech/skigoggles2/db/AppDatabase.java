package com.ymdrech.skigoggles2.db;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.RoomDatabase;

/**
 * Created by richard.mathias on 27/03/2018.
 */

@Database(
        entities = {
                Datapoint.class,
                Session.class},
        version = 1,
        exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract DatapointDao datapointDao();
    public abstract SessionDao sessionDao();
}
