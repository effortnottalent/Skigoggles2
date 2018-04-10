package com.ymdrech.skigoggles2.db;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.NonNull;

import java.util.Date;
import java.util.UUID;

import lombok.Data;

/**
 * Created by richard.mathias on 27/03/2018.
 */

@Data
@Entity
public class Session {

    @PrimaryKey
    @NonNull
    private String id = UUID.randomUUID().toString();
    private String name = "Unnamed session";
    private String partyId = "no party";
}
