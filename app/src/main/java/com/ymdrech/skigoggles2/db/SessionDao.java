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
public interface SessionDao {

    @Query("SELECT * FROM Session")
    public List<Session> find();

    @Insert
    public void save(Session session);

    @Update
    public int update(Session session);

    @Delete
    public void remove(Session session);
}
