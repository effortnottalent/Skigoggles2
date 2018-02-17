package com.ymdrech.skigoggles2;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.util.Log;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.data.kml.KmlLayer;
import com.ymdrech.skigoggles2.utils.Maps;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import static com.ymdrech.skigoggles2.MapsActivity.InfoWindowPage.PAGE_NEAREST_RUNS;
import static com.ymdrech.skigoggles2.MapsActivity.InfoWindowPage.PAGE_SPEED;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    public static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 2;
    public static final LatLng LATLNG_START_POS = new LatLng(51.5169, -3.5815);
    public static final String KML_PATH_IN_ASSET_FOLDER = "kml";

    private LocationManager locationManager;
    private LocationListener locationListener;
    private Polyline routePolyline;
    private TransformerFactory transformerFactory = TransformerFactory.newInstance();
    private Marker marker;
    private LocationBoard locationBoard;
    private InfoWindowPage shownPage;

    enum InfoWindowPage {
        PAGE_NEAREST_RUNS, PAGE_SPEED
    }

    private List<File> tempFilesToCleanupAfterTransform = new ArrayList<>();

    private Map<String, String> xsltTransformMap =
            Collections.unmodifiableMap(Stream.of(
                    Maps.entry("881467.kml", "three_valleys_snowheads.xslt"))
                    .collect(Maps.entriesToMap()));

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

    }

    @Override
    protected void onDestroy() {
        routePolyline.remove();
        super.onDestroy();
    }

    @Override
    public void onMapReady(final GoogleMap googleMap) {

        initMap(googleMap);
        registerLocationListener(googleMap);
        sortOutPermissionsForUpdatingLocation();
        loadRunsOntoMap(googleMap);
    }

    void initMap(final GoogleMap googleMap) {

        googleMap.clear();
        routePolyline = googleMap.addPolyline(new PolylineOptions());
        marker = googleMap.addMarker(new MarkerOptions()
                .position(LATLNG_START_POS)
                .title("")
                .snippet("")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
        googleMap.setOnInfoWindowClickListener(thatMarker -> {
            if(thatMarker.getId().equals(marker.getId())) {
                switchInfoWindow();
                updateInfoWindow(thatMarker);
            }
        });
        shownPage = InfoWindowPage.PAGE_SPEED;
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LATLNG_START_POS, 14f));

    }

    void registerLocationListener(final GoogleMap googleMap) {

        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                addNewLocation(googleMap, location);
            }
            public void onStatusChanged(String provider, int status, Bundle extras) {}
            public void onProviderEnabled(String provider) {}
            public void onProviderDisabled(String provider) {}
        };
        Log.i(getClass().getCanonicalName(), "registered location listener");

    }

    void sortOutPermissionsForUpdatingLocation() {

        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
            Log.i(getClass().getCanonicalName(), "asking for permission for fine location");
        } else {
            Log.i(getClass().getCanonicalName(), "got permission for fine location, requesting location updates");
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        switch(requestCode) {
            case MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(getClass().getCanonicalName(), "received okay for access fine location");
                    sortOutPermissionsForUpdatingLocation();
                }
        }

    }

    void addNewLocation(GoogleMap googleMap, Location location) {

        Log.i(getClass().getCanonicalName(), "adding location " + location);
        List<LatLng> points = routePolyline == null ? new ArrayList<>() : routePolyline.getPoints();
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        points.add(latLng);
        locationBoard.setLocationAndUpdateItems(location);
        routePolyline.setPoints(points);
        marker.setPosition(latLng);
        updateInfoWindow(marker);
        googleMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));

    }

    void switchInfoWindow() {

        switch (shownPage) {
            case PAGE_SPEED:
                shownPage = PAGE_NEAREST_RUNS;
                break;
            case PAGE_NEAREST_RUNS:
                shownPage = PAGE_SPEED;
                break;
        }
    }

    void updateInfoWindow(Marker marker) {
        switch (shownPage) {
            case PAGE_SPEED:
                marker.setTitle(
                        String.format("<b>Nearest runs within %sm</b>",
                                locationBoard.getMaxRangeMetres()));
                marker.setSnippet(locationBoard.toString());
                break;
            case PAGE_NEAREST_RUNS:
                marker.setSnippet(null);
                marker.setTitle(
                        String.format(Locale.ENGLISH, "%.0f km/h, %s",
                                locationBoard.getLocation().hasSpeed() ?
                                        locationBoard.getLocation().getSpeed() :
                                        locationBoard.getCalculatedSpeed(),
                                LocationBoard.bearingToCompassPoint(
                                        locationBoard.getLocation().hasBearing() ?
                                                locationBoard.getLocation().getBearing() :
                                                locationBoard.getCalculatedBearing()
                                )));
                break;
        }
        if(marker.isInfoWindowShown()) {
            marker.showInfoWindow();
        }

    }

    void loadRunsOntoMap(GoogleMap googleMap) {

        Set<KmlLayer> layerSet = new HashSet<>();
        try {
            for (String kml : getAssets().list(KML_PATH_IN_ASSET_FOLDER)) {
                try {
                    Log.i(getClass().getCanonicalName(), "loading kml file " + kml);
                    KmlLayer kmlLayer = new KmlLayer(googleMap, xsltTransform(kml),
                            getApplicationContext());
                    kmlLayer.addLayerToMap();
                    layerSet.add(kmlLayer);
                } catch (XmlPullParserException e) {
                    Log.e(getClass().getCanonicalName(), "couldn't parse kml", e);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            tempFilesToCleanupAfterTransform.forEach(File::delete);
            tempFilesToCleanupAfterTransform.clear();
        }
        locationBoard = new LocationBoard(layerSet);

    }

    InputStream xsltTransform(String kmlName) {

        String xsltTransformToUse = xsltTransformMap.get(kmlName);
        if(xsltTransformToUse != null) {
            try {
                Source xsltSource = new StreamSource(getAssets().open(xsltTransformToUse));
                Transformer transformer = transformerFactory.newTransformer(xsltSource);
                Source text = new StreamSource(getAssets().open(
                        KML_PATH_IN_ASSET_FOLDER + "/" + kmlName));
                File tempfile = File.createTempFile("temp-", "", getCacheDir());
                tempFilesToCleanupAfterTransform.add(tempfile);
                transformer.transform(text, new StreamResult(tempfile));
                return new FileInputStream(tempfile);
            } catch (IOException e) {
                Log.e(getClass().getCanonicalName(), "could load xslt", e);
            } catch (TransformerException e) {
                Log.e(getClass().getCanonicalName(), "transformer exception", e);
            }

        } else {

            try {
                return getAssets().open(KML_PATH_IN_ASSET_FOLDER + "/" + kmlName);
            } catch (IOException e) {
                Log.e(getClass().getCanonicalName(), "could load kml", e);
            }

        }

        return null;
    }

}
