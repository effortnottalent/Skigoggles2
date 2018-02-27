package com.ymdrech.skigoggles2;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.widget.Toast;

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
import com.google.maps.android.data.kml.KmlPlacemark;
import com.ymdrech.skigoggles2.location.LocationBoard;
import com.ymdrech.skigoggles2.location.dijkstra.Edge;
import com.ymdrech.skigoggles2.location.dijkstra.Graph;
import com.ymdrech.skigoggles2.location.dijkstra.KmlLayerAlgorithm;
import com.ymdrech.skigoggles2.location.dijkstra.Vertex;
import com.ymdrech.skigoggles2.utils.Maps;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import static com.ymdrech.skigoggles2.MapsActivity.InfoWindowPage.PAGE_SPEED;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleMap.OnPolylineClickListener {

    public static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 2;
    public static final LatLng LATLNG_START_POS = new LatLng(51.5169, -3.5815);
    public static final String KML_PATH_IN_ASSET_FOLDER = "kml";

    private LocationManager locationManager;
    private LocationListener locationListener;
    private Polyline routePolyline;
    private List<Polyline> shortestRoutePolylineList = new ArrayList<>();
    private List<Circle> shortestRoutePoints = new ArrayList<>();
    private TransformerFactory transformerFactory = TransformerFactory.newInstance();
    private Marker marker;
    private LocationBoard locationBoard;
    private InfoWindowPage shownPage;
    private KmlLayerAlgorithm kmlLayerAlgorithm;
    private GoogleMap googleMap;

    enum InfoWindowPage {
        PAGE_NEAREST_RUNS, PAGE_SPEED
    }

    private List<File> tempFilesToCleanupAfterTransform = new ArrayList<>();

    private Map<String, String> xsltTransformMap =
            Collections.unmodifiableMap(Stream.of(
                    Maps.entry("881467.kml", "three_valleys_snowheads.xslt"))
                    .collect(Maps.entriesToMap()));

    protected KmlLayerAlgorithm getKmlLayerAlgorithm() {
        return kmlLayerAlgorithm;
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
        registerLocationListener(googleMap);
        sortOutPermissionsForUpdatingLocation();
        loadRunsOntoMap(googleMap);
        addOnClickToRuns(googleMap);
        addRunNames(googleMap);
    }

    void addOnClickToRuns(final GoogleMap googleMap) {

        locationBoard.getKmlLayerSet().stream()
                .map(LocationBoard::getFlatListPlacemarks)
                .flatMap(List::stream)
                .collect(Collectors.toList())
                .forEach(kmlPlacemark -> {
                    if(kmlPlacemark.getInlineStyle() != null) {
                        kmlPlacemark.getPolylineOptions().clickable(true);
                    }
                });
        googleMap.setOnPolylineClickListener(this);
    }

    void addRunNames(final GoogleMap googleMap) {

        locationBoard.getKmlLayerSet().stream()
                .map(LocationBoard::getFlatListPlacemarks)
                .flatMap(List::stream)
                .collect(Collectors.toList())
                .forEach(kmlPlacemark -> {
                    List<LatLng> points = LocationBoard.getPoints(kmlPlacemark);
                    if(points.size() > 0 && kmlPlacemark.getProperty("name") != null) {
                        int pointIndex = Math.round(points.size() / 3);
                        double bearing = getBearingFromPointOnLine(points, pointIndex);
                        String name = kmlPlacemark.getProperty("name").toUpperCase();
                        Marker marker = googleMap.addMarker(new MarkerOptions()
                                .position(points.get(pointIndex))
                                .title(name)
                                .icon(createPureTextIcon(name, bearing)));
                        marker.setTag("noclick");
                    }
                });
    }

    Double getBearingFromPointOnLine(List<LatLng> points, int pointIndex) {
        switch(points.size()) {
            case 0: return null;
            case 1: return null;
            case 2: return SphericalUtil.computeHeading(points.get(0), points.get(1));
            default:
                return (SphericalUtil.computeHeading(points.get(pointIndex - 1),
                                points.get(pointIndex)) +
                        SphericalUtil.computeHeading(points.get(pointIndex),
                                points.get(pointIndex + 1))) / 2;
        }
    }

    BitmapDescriptor createPureTextIcon(String text, double bearing) {

        int stroke = 6;

        Paint strokePaint = new Paint();
        strokePaint.setTextSize(30);
        strokePaint.setColor(Color.argb(200, 255, 255, 255));
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(stroke);
        strokePaint.setAntiAlias(true);
        Paint textPaint = new Paint();
        textPaint.setTextSize(30);
        textPaint.setColor(Color.argb(200, 100, 100, 100));
        textPaint.setAntiAlias(true);

        float textWidth = strokePaint.measureText(text);
        float textHeight = strokePaint.getTextSize();
        int width = (int) (textWidth);
        int height = (int) (textHeight);

        Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        //canvas.rotate(Double.valueOf(bearing).floatValue());
        //canvas.drawColor(Color.WHITE);
        canvas.translate(0, height);
        canvas.drawText(text, 0, -stroke/2, strokePaint);
        canvas.drawText(text, 0, -stroke/2, textPaint);
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

    void registerLocationListener(final GoogleMap googleMap) {

        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                addNewLocation(googleMap, location);
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            public void onProviderEnabled(String provider) {
            }

            public void onProviderDisabled(String provider) {
            }
        };
        Log.i(getClass().getCanonicalName(), "registered location listener");

    }

    void sortOutPermissionsForUpdatingLocation() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
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

        switch (requestCode) {
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
        if (marker.isInfoWindowShown()) {
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
                    kmlLayerAlgorithm = new KmlLayerAlgorithm(kmlLayer);
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

    public void onPolylineClick(Polyline polyline) {

        if (locationBoard != null) {

            Graph graph = kmlLayerAlgorithm.getGraph();
            List<Vertex> oldVertices = new ArrayList<>(graph.getVertexes());
            List<Edge> oldEdges = new ArrayList<>(graph.getEdges());

            // source vertex

            KmlPlacemark placemark = locationBoard.getPlacemark();
            Vertex sourceVertex = KmlLayerAlgorithm.createNewVertex(
                    new LatLng(locationBoard.getLocation().getLatitude(),
                            locationBoard.getLocation().getLongitude()), placemark);
            graph.getVertexes().add(sourceVertex);
            if(placemark != null) {

                // add edge from source to end of current run
                // assume end of current run vertex is always present

                List<LatLng> points = LocationBoard.getPoints(placemark);
                Vertex endOfCurrentRunVertex = KmlLayerAlgorithm.createNewVertex(points.get(points.size() - 1),
                        placemark);
                Edge sourceEdge = KmlLayerAlgorithm.createNewEdge(sourceVertex, endOfCurrentRunVertex,
                        placemark);
                graph.getEdges().add(sourceEdge);

                // add edges from source to any midpoints on current run: TODO

            } else {

                // connect to nearest run via traverse
                throw new UnsupportedOperationException(
                        "not yet supported for source nodes off runs");

            }

            KmlPlacemark targetPlacemark = locationBoard.getKmlLayerSet().stream()
                    .map(LocationBoard::getFlatListPlacemarks)
                    .flatMap(List::stream)
                    .collect(Collectors.toList())
                    .stream()
                    .filter(kmlPlacemark -> polyline.getPoints().equals(
                            LocationBoard.getPoints(kmlPlacemark)))
                    .findFirst().get();

            // assume target vertex is always present

            Vertex targetVertex = KmlLayerAlgorithm.createNewVertex(
                    LocationBoard.getPoints(targetPlacemark).get(0), targetPlacemark);

            // reinit graph and execute

            kmlLayerAlgorithm.execute(sourceVertex);
            // kmlLayerAlgorithm.drawMap(googleMap);
            List<Vertex> route = kmlLayerAlgorithm.getPath(targetVertex);

            // reset graph

            kmlLayerAlgorithm.setGraph(new Graph(oldVertices, oldEdges));

            String routeString = "Can't find route...";
            if(route != null) {
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
                routeString = KmlLayerAlgorithm.formatVertexList(route);
            }
            Log.i(getClass().getCanonicalName(), routeString);
            Toast.makeText(this, routeString, Toast.LENGTH_LONG).show();

        }

    }
}