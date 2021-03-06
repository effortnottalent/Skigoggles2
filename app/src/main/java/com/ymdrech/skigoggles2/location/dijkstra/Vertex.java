package com.ymdrech.skigoggles2.location.dijkstra;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.data.kml.KmlPlacemark;

import lombok.Data;

@Data
public class Vertex {

    final double MARGIN = 0.00000001d;

    private final LatLng latLng;
    private final KmlPlacemark placemark;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LatLng otherLatLng = ((Vertex) o).getLatLng();

        return latLng != null ?
                Math.abs(latLng.longitude - otherLatLng.longitude) < MARGIN ||
                        Math.abs(latLng.latitude - otherLatLng.latitude) < MARGIN
                : otherLatLng == null;
    }

    @Override
    public int hashCode() {
        return latLng != null ? latLng.hashCode() : 0;
    }

}