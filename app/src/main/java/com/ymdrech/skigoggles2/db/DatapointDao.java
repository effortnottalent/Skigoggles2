package com.ymdrech.skigoggles2.db;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import java.util.List;

/**
 * Created by richard.mathias on 01/04/2018.
 */

@Dao
public interface DatapointDao {

    @Query("SELECT * FROM Datapoint ORDER BY timestamp ASC")
    List<Datapoint> find();

    @Query("SELECT * FROM Datapoint WHERE runName = :runName ORDER BY timestamp ASC")
    List<Datapoint> findByRunName(String runName);

    @Insert
    public void save(Datapoint session);

    @Update
    public int update(Datapoint session);

    @Delete
    public void remove(Datapoint session);
}
