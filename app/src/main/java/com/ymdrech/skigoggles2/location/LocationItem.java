package com.ymdrech.skigoggles2.location;

import com.google.maps.android.data.kml.KmlPlacemark;

import lombok.Data;

/**
 * Created by richard.mathias on 14/02/2018.
 */

@Data
public class LocationItem {

    private KmlPlacemark placemark;
    private double bearingFromStart;
    private double distanceFromStart;

}
