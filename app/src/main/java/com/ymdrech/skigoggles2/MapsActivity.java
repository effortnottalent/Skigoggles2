package com.ymdrech.skigoggles2;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.util.Log;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.SphericalUtil;
import com.google.maps.android.data.kml.KmlLayer;
import com.ymdrech.skigoggles2.components.LocationBroadcastComponent;
import com.ymdrech.skigoggles2.components.LocationBroadcastDto;
import com.ymdrech.skigoggles2.location.LocationBoard;
import com.ymdrech.skigoggles2.location.LocationItem;
import com.ymdrech.skigoggles2.location.dijkstra.KmlLayerAlgorithm;
import com.ymdrech.skigoggles2.location.dijkstra.Vertex;
import com.ymdrech.skigoggles2.services.LocationCallbacks;
import com.ymdrech.skigoggles2.services.LocationService;
import com.ymdrech.skigoggles2.utils.Maps;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import static com.ymdrech.skigoggles2.MapsActivity.InfoWindowPage.PAGE_NEAREST_RUNS;
import static com.ymdrech.skigoggles2.MapsActivity.InfoWindowPage.PAGE_PARTY;
import static com.ymdrech.skigoggles2.MapsActivity.InfoWindowPage.PAGE_SPEED;
import static com.ymdrech.skigoggles2.location.LocationBoard.bearingToCompassPoint;
import static com.ymdrech.skigoggles2.services.LocationService.MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleMap.OnPolylineClickListener, LocationCallbacks {

    public static final LatLng LATLNG_START_POS = new LatLng(51.5169, -3.5815);
    public static final String KML_PATH_IN_ASSET_FOLDER = "kml";

    private final String TAG = getClass().getCanonicalName();

    private Polyline routePolyline;
    private List<Polyline> shortestRoutePolylineList = new ArrayList<>();
    private List<Circle> shortestRoutePoints = new ArrayList<>();
    private TransformerFactory transformerFactory = TransformerFactory.newInstance();
    private Marker marker;
    private InfoWindowPage shownPage;
    private GoogleMap googleMap;
    private LocationService locationService;
    private boolean bound;
    private Set<KmlLayer> layerSet = new HashSet<>();

    enum InfoWindowPage {
        PAGE_NEAREST_RUNS, PAGE_SPEED, PAGE_PARTY
    }

    private List<File> tempFilesToCleanupAfterTransform = new ArrayList<>();

    private Map<String, String> xsltTransformMap =
            Collections.unmodifiableMap(Stream.of(
                    Maps.entry("881467.kml", "three_valleys_snowheads.xslt"))
                    .collect(Maps.entriesToMap()));

    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            LocationService.LocalBinder binder = (LocationService.LocalBinder) service;
            locationService = binder.getService();
            bound = true;
            initialiseLocationService();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            bound = false;
        }
    };

    private void initialiseLocationService() {

        locationService.setLocationCallbacks(this);
        locationService.requestLocationUpdates(this);
        layerSet.forEach(locationService::processKmlLayer);
        addOnClickToRuns(googleMap);
        addRunNames(googleMap);
        locationService.startInForeground(this);

    }

    @Override
    public void receiveLocationUpdate(Location location) {
        addNewLocation(googleMap, location);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, LocationService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (bound) {
            locationService.setLocationCallbacks(null); // unregister
            unbindService(serviceConnection);
            bound = false;
        }
    }

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

        this.googleMap = googleMap;
        initMap(googleMap);
        loadRunsOntoMap(googleMap);
    }

    void addOnClickToRuns(final GoogleMap googleMap) {

        locationService.getLocationBoard().getAllPlacemarks()
                .forEach(kmlPlacemark -> {
                    if(kmlPlacemark.getInlineStyle() != null) {
                        kmlPlacemark.getPolylineOptions().clickable(true);
                    }
                });
        googleMap.setOnPolylineClickListener(this);
    }

    void addRunNames(final GoogleMap googleMap) {

        locationService.getLocationBoard().getAllPlacemarks()
                .forEach(kmlPlacemark -> {
                    List<LatLng> points = LocationBoard.getPoints(kmlPlacemark);
                    if(points.size() > 0 && kmlPlacemark.getProperty("name") != null) {
                        int pointIndex = Math.round(points.size() / 2);
                        LatLng point = pointIndex == 1 ?
                                SphericalUtil.interpolate(points.get(0), points.get(1), 0.5)
                                : points.get(pointIndex);
                        float bearing = getBearingFromPointOnLine(points, pointIndex);
                        String name = kmlPlacemark.getProperty("name").toUpperCase();
                        Marker marker = googleMap.addMarker(new MarkerOptions()
                                .position(point)
                                .flat(true)
                                .icon(createPureTextIcon(name))
                                .anchor(0.5f, 0.5f)
                                .rotation(bearing - 90)
                                .title(name)
                        );
                        marker.setTag("noclick");
                    }
                });
    }

    float getBearingFromPointOnLine(List<LatLng> points, int pointIndex) {
        switch(points.size()) {
            case 0: return 0f;
            case 1: return 0f;
            case 2: return Double.valueOf(SphericalUtil.computeHeading(points.get(0),
                    points.get(1))).floatValue();
            default:
                return Double.valueOf((SphericalUtil.computeHeading(points.get(pointIndex - 1),
                                points.get(pointIndex)) +
                        SphericalUtil.computeHeading(points.get(pointIndex),
                                points.get(pointIndex + 1))) / 2).floatValue();
        }
    }

    BitmapDescriptor createPureTextIcon(String text) {

        int stroke = 4;

        Paint strokePaint = new Paint();
        strokePaint.setTextSize(18);
        strokePaint.setColor(Color.argb(225, 255, 255, 255));
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(stroke);
        strokePaint.setAntiAlias(true);

        Paint textPaint = new Paint();
        textPaint.setTextSize(18);
        textPaint.setColor(Color.argb(200, 0, 0, 0));
        textPaint.setAntiAlias(true);

        Rect textR = new Rect();
        strokePaint.getTextBounds(text, 0, text.length(), textR);

        int width = textR.width() + stroke;
        int height = textR.height() + stroke;

        Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        canvas.translate(0, height);
        canvas.drawText(text, 0, - stroke / 2, strokePaint);
        canvas.drawText(text,  0, - stroke / 2, textPaint);
        return BitmapDescriptorFactory.fromBitmap(image);
    }

    void initMap(final GoogleMap googleMap) {

        googleMap.clear();
        googleMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
        routePolyline = googleMap.addPolyline(new PolylineOptions());
        marker = googleMap.addMarker(new MarkerOptions()
                .position(LATLNG_START_POS)
                .title("")
                .snippet("")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
        googleMap.setOnInfoWindowClickListener(thatMarker -> {
            if (thatMarker.getId().equals(marker.getId())) {
                switchInfoWindow();
                updateInfoWindow(thatMarker);
            }
        });
        shownPage = InfoWindowPage.PAGE_SPEED;
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LATLNG_START_POS, 14f));

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "received okay for access fine location");
                    locationService.requestLocationUpdates(this);
                }
        }

    }

    void addNewLocation(GoogleMap googleMap, Location location) {

        LocationBoard locationBoard = locationService.getLocationBoard();
        List<LatLng> points = routePolyline == null ? new ArrayList<>() : routePolyline.getPoints();
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        points.add(latLng);
        if(locationBoard.getLocation() == null ||
                locationBoard.getLocation().getLongitude() != location.getLongitude() ||
                locationBoard.getLocation().getLatitude() != location.getLatitude()) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        }
        locationBoard.setLocationAndUpdateItems(location);
        routePolyline.setPoints(points);
        marker.setPosition(latLng);
        updateInfoWindow(marker);

    }

    void switchInfoWindow() {

        switch (shownPage) {
            case PAGE_SPEED:
                shownPage = PAGE_NEAREST_RUNS;
                break;
            case PAGE_NEAREST_RUNS:
                shownPage = PAGE_PARTY;
                break;
            case PAGE_PARTY:
                shownPage = PAGE_SPEED;
                break;
        }
    }

    void updateInfoWindow(Marker marker) {

        LocationBoard locationBoard = locationService.getLocationBoard();
        switch (shownPage) {
            case PAGE_SPEED:
                marker.setTitle(String.format("<b>Nearest runs within %sm</b>",
                                locationBoard.getMaxRangeMetres()));
                marker.setSnippet(getLocationBoardSummary(locationBoard));
                break;
            case PAGE_NEAREST_RUNS:
                marker.setSnippet(null);
                marker.setTitle(
                        String.format(Locale.ENGLISH, "%.0f m/s, %s",
                                locationBoard.getLocation().hasSpeed() ?
                                        locationBoard.getLocation().getSpeed() :
                                        locationBoard.getCalculatedSpeed(),
                                bearingToCompassPoint(
                                        locationBoard.getLocation().hasBearing() ?
                                                locationBoard.getLocation().getBearing() :
                                                locationBoard.getCalculatedBearing()
                                )));
                break;
            case PAGE_PARTY:
                marker.setTitle("<b>People in your party</b>");
                marker.setSnippet(getLocationRegistrySummary(
                        locationService.getLocationBroadcastComponent()));
                break;
        }
        if (marker.isInfoWindowShown()) {
            marker.showInfoWindow();
        }

    }

    String getLocationBoardSummary(LocationBoard locationBoard) {

        return Arrays.asList(
                locationBoard.getLocationItems().toArray(new LocationItem[0])).stream()
                .sorted(Comparator.comparing(LocationItem::getDistanceFromStart))
                .map(locationItem ->
                        String.format(Locale.ENGLISH, "%s (%s): %.0fm %s",
                                locationItem.getPlacemark().getProperty("name"),
                                locationItem.getPlacemark().getProperty("description"),
                                locationItem.getDistanceFromStart(),
                                bearingToCompassPoint(locationItem.getBearingFromStart())))
                .distinct()
                .collect(Collectors.joining("<br/>"));
    }

    String getLocationRegistrySummary(LocationBroadcastComponent locationBroadcastComponent) {

        LocationBoard locationBoard = locationService.getLocationBoard();
        return locationBroadcastComponent.getLocationRegistry().entrySet().stream()
                .map(entry -> {
                    LocationBroadcastDto value = entry.getValue();
                    LatLng ourLatLng = new LatLng(locationBoard.getLocation().getLatitude(),
                            locationBoard.getLocation().getLongitude());
                    LatLng theirLatLng = new LatLng(value.getLatitude(), value.getLongitude());
                    double distance = SphericalUtil.computeDistanceBetween(
                            ourLatLng, theirLatLng);
                    double bearing = SphericalUtil.computeHeading(ourLatLng, theirLatLng);
                    return String.format(Locale.ENGLISH,
                            "%s: %s (%s), %.0f %s",
                            entry.getValue().getUsername(),
                            entry.getValue().getRunName(),
                            entry.getValue().getRunType(),
                            distance, bearingToCompassPoint(bearing));
                }).collect(Collectors.joining("<br/>"));
    }

    void loadRunsOntoMap(GoogleMap googleMap) {

        try {
            for (String kml : getAssets().list(KML_PATH_IN_ASSET_FOLDER)) {
                try {
                    Log.i(TAG, "loading kml file " + kml);
                    KmlLayer kmlLayer = new KmlLayer(googleMap, xsltTransform(kml),
                            getApplicationContext());
                    kmlLayer.addLayerToMap();
                    layerSet.add(kmlLayer);
                } catch (XmlPullParserException e) {
                    Log.e(TAG, "couldn't parse kml", e);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            tempFilesToCleanupAfterTransform.forEach(File::delete);
            tempFilesToCleanupAfterTransform.clear();
        }

    }

    InputStream xsltTransform(String kmlName) {

        String xsltTransformToUse = xsltTransformMap.get(kmlName);
        if (xsltTransformToUse != null) {
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
                Log.e(TAG, "could load xslt", e);
            } catch (TransformerException e) {
                Log.e(TAG, "transformer exception", e);
            }

        } else {

            try {
                return getAssets().open(KML_PATH_IN_ASSET_FOLDER + "/" + kmlName);
            } catch (IOException e) {
                Log.e(TAG, "could load kml", e);
            }

        }

        return null;
    }

    void renderRouteOnMap(List<Vertex> route) {

        shortestRoutePoints.forEach(Circle::remove);
        shortestRoutePolylineList.forEach(Polyline::remove);
        route.forEach(vertex -> {
            Circle circleOptions = googleMap.addCircle(new CircleOptions()
                    .center(vertex.getLatLng())
                    .radius(30d)
                    .zIndex(1000)
                    .fillColor(Color.argb(200,255,255,255))
                    .strokeColor(Color.WHITE));
            shortestRoutePoints.add(circleOptions);
            if(vertex.getPlacemark() != null) {
                Polyline polyline2 = googleMap.addPolyline(new PolylineOptions()
                        .color(Color.argb(100,255,255,255))
                        .width(20)
                        .zIndex(1000)
                        .addAll(LocationBoard.getPoints(vertex.getPlacemark())));
                shortestRoutePolylineList.add(polyline2);
            }
        });
    }

    public void onPolylineClick(Polyline polyline) {
        List<Vertex> shortestRoute = locationService.getShortestRoute(polyline);
        renderRouteOnMap(shortestRoute);
    }
}