package com.ymdrech.skigoggles2.components;

import android.location.Location;

import com.google.maps.android.data.kml.KmlPlacemark;

import java.util.Date;

import lombok.Data;

/**
 * Created by richard.mathias on 02/03/2018.
 */

@Data
public class LocationBroadcastDto {

    private String username;
    private String userId;
    private double longitude;
    private double latitude;
    private double altitude;
    private double accuracy;
    private double bearing;
    private double speed;
    private Date date;
    private String runName;
    private String runType;
    private String partyId;

}
