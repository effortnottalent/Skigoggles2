package com.ymdrech.skigoggles2.db;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;

import java.util.Date;

import lombok.Data;

/**
 * Created by richard.mathias on 27/03/2018.
 */

@Data
@Entity
public class Session {

    @PrimaryKey(autoGenerate = true)
    private int id;
    private String name = "Unnamed session";
}
