package com.ymdrech.skigoggles2.db;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.NonNull;

import java.util.Date;
import java.util.UUID;

import lombok.Data;

/**
 * Created by richard.mathias on 27/03/2018.
 */

@Entity(indices = {
            @Index("sessionId"),
            @Index("id")
        },
        foreignKeys = @ForeignKey(
            entity = Session.class,
            parentColumns = "id",
            childColumns = "sessionId"))
@Data
public class Datapoint {

    @PrimaryKey
    @NonNull
    private String id = UUID.randomUUID().toString();
    private double longitude;
    private double latitude;
    private double altitude;
    private double bearing;
    private double speed;
    private String runName;
    private String runType;
    private long timestamp;
    private String sessionId;
}
