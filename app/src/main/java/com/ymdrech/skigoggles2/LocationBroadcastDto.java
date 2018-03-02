package com.ymdrech.skigoggles2;

import android.location.Location;

import com.google.maps.android.data.kml.KmlPlacemark;

import java.util.Date;

import lombok.Data;

/**
 * Created by richard.mathias on 02/03/2018.
 */

@Data
public class LocationBroadcastDto {

    private String userId;
    private Location location;
    private Date date;
    private KmlPlacemark placemark;

}
