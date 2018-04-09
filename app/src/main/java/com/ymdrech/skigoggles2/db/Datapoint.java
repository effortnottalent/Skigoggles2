package com.ymdrech.skigoggles2.db;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;

import java.util.Date;

import lombok.Data;

/**
 * Created by richard.mathias on 27/03/2018.
 */

@Entity(indices = @Index("sessionId"),
        foreignKeys = @ForeignKey(
            entity = Session.class,
            parentColumns = "id",
            childColumns = "sessionId"))
@Data
public class Datapoint {

    @PrimaryKey(autoGenerate = true)
    private int id;
    private double longitude;
    private double latitude;
    private double altitude;
    private double bearing;
    private double speed;
    private String runName;
    private String runType;
    private long timestamp;
    private int sessionId;
}
