package com.ymdrech.skigoggles2.location;

import android.location.Location;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.SphericalUtil;
import com.google.maps.android.data.kml.KmlContainer;
import com.google.maps.android.data.kml.KmlLayer;
import com.google.maps.android.data.kml.KmlPlacemark;

import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Data;

/**
 * Created by richard.mathias on 14/02/2018.
 */

@Data
public class LocationBoard {

    public static final float RUN_THRESHOLD_METRES = 100;
    public static final float DEFAULT_MAX_RANGE_METRES = 1000;

    private final String TAG = getClass().getCanonicalName();
    
    private Location location;
    private KmlPlacemark currentPlacemark;
    private Set<LocationItem> locationItems = new HashSet<>();
    private float maxRangeMetres = DEFAULT_MAX_RANGE_METRES;
    private Set<KmlPlacemark> allPlacemarks = new HashSet<>();
    private double calculatedSpeed;
    private double calculatedBearing;
    private double sessionDistance = 0.0;
    private double sessionTimeSeconds = 0.0;

    public void setLocationItems(Set<LocationItem> locationItems) {
        this.locationItems = locationItems;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocationAndUpdateItems(Location location) {
        calculatedSpeed = getInstantaneousSpeed(this.location, location);
        calculatedBearing = getCalculatedBearing(this.location, location);
        if(this.location != null) {
            sessionTimeSeconds += (location.getTime() - this.location.getTime()) / 1000;
            sessionDistance += SphericalUtil.computeDistanceBetween(
                    new LatLng(this.location.getLatitude(), this.location.getLongitude()),
                    new LatLng(location.getLatitude(), location.getLongitude()));
        }
        this.location = location;
        updateLocationItems();
        this.currentPlacemark = findNearestKmlPlacemark();
    }

    KmlPlacemark findNearestKmlPlacemark() {

        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        Map<KmlPlacemark, Double> placemarkDistances = new HashMap<>();
        allPlacemarks.stream()
                .filter(kmlPlacemark -> getPoints(kmlPlacemark).size() != 0)
                .forEach(kmlPlacemark ->
                    placemarkDistances.put(kmlPlacemark,
                            SphericalUtil.computeDistanceBetween(latLng,
                                    getPoints(kmlPlacemark).stream()
                                            .min(Comparator.comparing(
                                                    point -> SphericalUtil.computeDistanceBetween(
                                                            latLng, point)))
                                            .get())));
        return placemarkDistances.entrySet().stream()
                .min(Comparator.comparing(Map.Entry::getValue))
                .get().getKey();

    }

    private double getCalculatedBearing(Location beforeLocation, Location afterLocation) {

        if(beforeLocation == null || afterLocation == null) {
            return 0.0;
        }
        return SphericalUtil.computeHeading(
                new LatLng(beforeLocation.getLatitude(), beforeLocation.getLongitude()),
                new LatLng(afterLocation.getLatitude(), afterLocation.getLongitude()));

    }

    private double getInstantaneousSpeed(Location beforeLocation, Location afterLocation) {

        if(beforeLocation == null || afterLocation == null) {
            return 0.0;
        }
        double distance = SphericalUtil.computeDistanceBetween(
                new LatLng(beforeLocation.getLatitude(), beforeLocation.getLongitude()),
                new LatLng(afterLocation.getLatitude(), afterLocation.getLongitude()));
        double time = afterLocation.getElapsedRealtimeNanos() - beforeLocation.getElapsedRealtimeNanos();
        return distance / time * 1e9;

    }

    public static List<LatLng> getPoints(KmlPlacemark kmlPlacemark) {

        Object geometryObject = kmlPlacemark.getGeometry().getGeometryObject();
        return geometryObject instanceof List ? (List<LatLng>) geometryObject : new ArrayList<>();
    }

    private void updateLocationItems() {

        if(location == null) {
            return;
        }
        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        Set<LocationItem> newLocationItems = new HashSet<>();
        allPlacemarks.forEach(kmlPlacemark -> {
            List<LatLng> points = getPoints(kmlPlacemark);
            if (points.size() != 0) {
                double distanceFromStartMetres = SphericalUtil.computeDistanceBetween(
                        currentLatLng, points.get(0));
                if(distanceFromStartMetres < maxRangeMetres) {
                    LocationItem locationItem = new LocationItem();
                    locationItem.setDistanceFromStart(distanceFromStartMetres);
                    locationItem.setPlacemark(kmlPlacemark);
                    locationItem.setBearingFromStart(
                            SphericalUtil.computeHeading(currentLatLng, points.get(0)));
                    if(!newLocationItems.contains(locationItem)) {
                        newLocationItems.add(locationItem);
                    }
                }
            }
        });
        if(newLocationItems != locationItems) {
            locationItems = newLocationItems;
        }

    }

    public static String bearingToCompassPoint(double bearing) {
        String[] compassPoints = {
                "N", "NNE", "NE", "ENE",
                "E", "ESE", "SE", "SSE",
                "S", "SSW", "SW", "WSW",
                "W", "WNW", "NW", "NNW" };
        int index = Long.valueOf(Math.round((bearing + 360) / 22.5)).intValue() % 16;
        return compassPoints[index];
    }

    public static List<KmlPlacemark> getFlatListPlacemarks(KmlLayer kmlLayer) {

        List<KmlPlacemark> placemarks = new ArrayList<>();
        kmlLayer.getContainers().forEach(container ->
            CollectionUtils.addAll(placemarks, getFlatListPlacemarks(container)));
        return placemarks;
    }

    private static List<KmlPlacemark> getFlatListPlacemarks(KmlContainer kmlContainer) {

        List<KmlPlacemark> placemarks = new ArrayList<>();
        CollectionUtils.addAll(placemarks, kmlContainer.getPlacemarks());
        kmlContainer.getContainers().forEach(container ->
                CollectionUtils.addAll(placemarks, getFlatListPlacemarks(container)));
        return placemarks.stream()
                .distinct()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

}
