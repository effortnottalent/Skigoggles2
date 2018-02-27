package com.ymdrech.skigoggles2.location;

import com.google.maps.android.data.kml.KmlPlacemark;

/**
 * Created by richard.mathias on 14/02/2018.
 */

public class LocationItem {

    private KmlPlacemark placemark;
    private double bearingFromStart;
    private double distanceFromStart;

    public KmlPlacemark getPlacemark() {
        return placemark;
    }

    public void setPlacemark(KmlPlacemark placemark) {
        this.placemark = placemark;
    }

    public double getBearingFromStart() {
        return bearingFromStart;
    }

    public void setBearingFromStart(double bearingFromStart) {
        this.bearingFromStart = bearingFromStart;
    }

    public double getDistanceFromStart() {
        return distanceFromStart;
    }

    public void setDistanceFromStart(double distanceFromStart) {
        this.distanceFromStart = distanceFromStart;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LocationItem that = (LocationItem) o;

        if (Double.compare(that.bearingFromStart, bearingFromStart) != 0) return false;
        if (Double.compare(that.distanceFromStart, distanceFromStart) != 0) return false;
        return placemark != null ? placemark.equals(that.placemark) : that.placemark == null;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = placemark != null ? placemark.hashCode() : 0;
        temp = Double.doubleToLongBits(bearingFromStart);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(distanceFromStart);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }
}
