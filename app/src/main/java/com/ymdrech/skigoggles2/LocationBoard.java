package com.ymdrech.skigoggles2;

import android.location.Location;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.SphericalUtil;
import com.google.maps.android.data.kml.KmlContainer;
import com.google.maps.android.data.kml.KmlLayer;
import com.google.maps.android.data.kml.KmlPlacemark;

import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Created by richard.mathias on 14/02/2018.
 */

public class LocationBoard {

    public static final float DEFAULT_MAX_RANGE_METRES = 1000;

    private Location location;
    private Set<LocationItem> locationItems = new HashSet<>();
    private float maxRangeMetres = DEFAULT_MAX_RANGE_METRES;
    private Set<KmlLayer> kmlLayerSet;

    public Set<KmlLayer> getKmlLayerSet() {
        return kmlLayerSet;
    }

    public void setKmlLayerSet(Set<KmlLayer> kmlLayerSet) {
        this.kmlLayerSet = kmlLayerSet;
    }

    public LocationBoard(Location location, float maxRangeMetres, Set<KmlLayer> kmlLayerSet) {
        this.location = location;
        this.maxRangeMetres = maxRangeMetres;
        this.kmlLayerSet = kmlLayerSet;
    }

    public LocationBoard(Location location, Set<KmlLayer> kmlLayerSet) {
        this.location = location;
        this.kmlLayerSet = kmlLayerSet;
    }

    public LocationBoard(Set<KmlLayer> kmlLayerSet) {
        this.kmlLayerSet = kmlLayerSet;
    }

    public void setLocationItems(Set<LocationItem> locationItems) {
        this.locationItems = locationItems;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocationAndUpdateItems(Location location) {
        this.location = location;
        updateLocationItems();
    }

    public Set<LocationItem> getLocationItems() {
        return locationItems;
    }

    public float getMaxRangeMetres() {
        return maxRangeMetres;
    }

    public void setMaxRangeMetres(float maxRangeMetres) {
        this.maxRangeMetres = maxRangeMetres;
    }

    protected void updateLocationItems() {

        if(location == null) {
            return;
        }
        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        Set<LocationItem> newLocationItems = new HashSet<>();
        kmlLayerSet.forEach(kmlLayer -> {
            getFlatListPlacemarks(kmlLayer).forEach(kmlPlacemark -> {
                Object geometryObject = kmlPlacemark.getGeometry().getGeometryObject();
                List<LatLng> points = geometryObject instanceof List ?
                        (List<LatLng>) geometryObject : new ArrayList<>();
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
        });
        if(newLocationItems != locationItems) {
            locationItems = newLocationItems;
        }

    }

    public String toString() {

        return Arrays.asList(
                locationItems.toArray(new LocationItem[0])).stream()
                .sorted(Comparator.comparing(LocationItem::getDistanceFromStart))
                .map(locationItem ->
                        String.format("%s (%s): %.0fm %s",
                                locationItem.getPlacemark().getProperty("name"),
                                locationItem.getPlacemark().getProperty("description"),
                                locationItem.getDistanceFromStart(),
                                bearingToCompassPoint(locationItem.getBearingFromStart())))
                .distinct()
                .collect(Collectors.joining("<br/>"));

    }

    private String bearingToCompassPoint(double bearing) {
        String[] compassPoints = {
                "N", "NNE", "NE", "ENE",
                "E", "ESE", "SE", "SSE",
                "S", "SSW", "SW", "WSW",
                "W", "WNW", "NW", "NNW" };
        int index = Long.valueOf(Math.round((bearing + 360) / 22.5)).intValue() % 16;
        Log.d(String.format("bearing: %.2f -> index: %d", bearing, index),
                getClass().getCanonicalName());
        return compassPoints[index];
    }

    private List<KmlPlacemark> getFlatListPlacemarksSimple(KmlLayer kmlLayer) {

        List<KmlPlacemark> placemarks = new ArrayList<>();
        kmlLayer.getContainers().forEach(container -> {
            CollectionUtils.addAll(placemarks, container.getPlacemarks());
            container.getContainers().forEach(container2 -> {
                CollectionUtils.addAll(placemarks, container2.getPlacemarks());
                container2.getContainers().forEach(container3 -> {
                    CollectionUtils.addAll(placemarks, container3.getPlacemarks());
                });
            });
        });
        return placemarks;

    }

    private List<KmlPlacemark> getFlatListPlacemarks(KmlLayer kmlLayer) {

        List<KmlPlacemark> placemarks = new ArrayList<>();
        kmlLayer.getContainers().forEach(container ->
            CollectionUtils.addAll(placemarks, getFlatListPlacemarks(container)));
        return placemarks;
    }

    private List<KmlPlacemark> getFlatListPlacemarks(KmlContainer kmlContainer) {

        List<KmlPlacemark> placemarks = new ArrayList<>();
        CollectionUtils.addAll(placemarks, kmlContainer.getPlacemarks());
        kmlContainer.getContainers().forEach(container ->
                CollectionUtils.addAll(placemarks, getFlatListPlacemarks(container)));
        return placemarks.stream()
                .distinct()
                .filter(kmlPlacemark -> kmlPlacemark != null)
                .collect(Collectors.toList());
    }

}
