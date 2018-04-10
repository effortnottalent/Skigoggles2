package com.ymdrech.skigoggles2.services;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.arch.persistence.room.Room;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polyline;
import com.google.maps.android.SphericalUtil;
import com.google.maps.android.data.kml.KmlLayer;
import com.google.maps.android.data.kml.KmlPlacemark;
import com.ymdrech.skigoggles2.NotificationItem;
import com.ymdrech.skigoggles2.R;
import com.ymdrech.skigoggles2.User;
import com.ymdrech.skigoggles2.components.LocationBroadcastComponent;
import com.ymdrech.skigoggles2.db.AppDatabase;
import com.ymdrech.skigoggles2.db.Datapoint;
import com.ymdrech.skigoggles2.db.DatapointDao;
import com.ymdrech.skigoggles2.db.Session;
import com.ymdrech.skigoggles2.db.SessionDao;
import com.ymdrech.skigoggles2.location.LocationBoard;
import com.ymdrech.skigoggles2.location.Party;
import com.ymdrech.skigoggles2.location.dijkstra.Edge;
import com.ymdrech.skigoggles2.location.dijkstra.Graph;
import com.ymdrech.skigoggles2.location.dijkstra.KmlLayerAlgorithm;
import com.ymdrech.skigoggles2.location.dijkstra.Vertex;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;

import static com.ymdrech.skigoggles2.location.LocationBoard.bearingToCompassPoint;

public class LocationService extends Service {

    public static final double METRES_TO_CHECK_FOR_NEXT_NODE = 200d;
    public static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 2;
    public static final double SECONDS_END_OF_RUN_FAFF = 120d;
    public static final double SECONDS_START_OF_CHAIRLIFT_FAFF = 120d;
    public static final int ONGOING_NOTIFICATION_ID = 1;

    private LocationManager locationManager;
    private final IBinder binder = new LocalBinder();
    private final String TAG = getClass().getCanonicalName();

    private final Random random = new Random();
    private AppDatabase db;
    private Session session;

    private LocationListener locationListener;
    private User user;
    private Party party;
    @Getter private LocationBroadcastComponent locationBroadcastComponent;
    private List<NotificationItem> notifications = new ArrayList<>();
    private List<Vertex> shortestRoute;
    @Getter private LocationBoard locationBoard = new LocationBoard();
    @Setter private LocationCallbacks locationCallbacks;
    private KmlLayerAlgorithm kmlLayerAlgorithm;

    private Map<String, Integer> notificationRegistry = new HashMap<>();

    public class LocalBinder extends Binder {
        public LocationService getService() {
            return LocationService.this;
        }
    }

    @Override
    public void onCreate() {
        createUser();
        createParty();
        loadDatabase();
        startSession();
        locationBroadcastComponent = new LocationBroadcastComponent(user, party, this);
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        registerLocationListener();
        super.onCreate();
    }

    public void loadDatabase() {
        db = Room.databaseBuilder(getApplicationContext(),
                AppDatabase.class, "skigoggles2").build();
    }

    public void startInForeground(Activity activity) {
        Intent notificationIntent = new Intent(this, this.getClass());
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Notification.Builder notificationBuilder =
                new Notification.Builder(this)
                        .setContentTitle(getText(R.string.notification_title))
                        .setContentText(getText(R.string.notification_message))
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentIntent(pendingIntent)
                        .setGroup(getString(R.string.notification_group_service))
                        .setTicker(getText(R.string.ticker_text));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationBuilder.setChannelId(getString(R.string.channel_id_normal));
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;

            NotificationChannel notificationChannelNormal =
                    new NotificationChannel(getString(R.string.channel_id_normal), name, importance);
            notificationChannelNormal.setDescription(description);
            notificationChannelNormal.setImportance(NotificationManager.IMPORTANCE_DEFAULT);
            NotificationChannel notificationChannelImportant =
                    new NotificationChannel(getString(R.string.channel_id_important), name, importance);
            notificationChannelImportant.setDescription(description);
            notificationChannelImportant.setImportance(NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(notificationChannelNormal);
            notificationManager.createNotificationChannel(notificationChannelImportant);
        }

        startForeground(ONGOING_NOTIFICATION_ID, notificationBuilder.build());
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    void createUser() {
        user = new User();
        user.setId("stevegnarly");
        user.setName("Steve Gnarly");
    }

    void createParty() {
        party = new Party();
        party.setId(UUID.randomUUID().toString());
    }

    void registerLocationListener() {

        locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                if(locationCallbacks != null) {
                    Log.d(TAG, String.format("got new location %s", location));
                    locationCallbacks.receiveLocationUpdate(location);
                    locationBroadcastComponent.broadcastLocation(user, locationBoard);
                    recordLocation(location);
                    addNotificationApproachingNextNode();
                }
            }
            public void onStatusChanged(String provider, int status, Bundle extras) {}
            public void onProviderEnabled(String provider) {}
            public void onProviderDisabled(String provider) {}
        };
        Log.i(TAG, "registered location listener");

    }

    void startSession() {
        session = new Session();
        session.setPartyId(party.getId());
        session.setName(String.format("Test Session - %s", new Date()));
        new Thread(() -> db.sessionDao().save(session)).start();

    }

    void recordLocation(Location location) {

        Datapoint datapoint = new Datapoint();
        datapoint.setAltitude(location.getAltitude());
        datapoint.setBearing(location.getBearing());
        datapoint.setLatitude(location.getLatitude());
        datapoint.setLongitude(location.getLongitude());
        if(locationBoard.getCurrentPlacemark() != null) {
            datapoint.setRunName(locationBoard.getCurrentPlacemark().getProperty("name"));
        }
        datapoint.setSessionId(session.getId());
        datapoint.setSpeed(location.getSpeed());
        datapoint.setTimestamp(new Date().getTime());
        new Thread(() -> db.datapointDao().save(datapoint)).start();

    }

    public void requestLocationUpdates(Activity activity) {

        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
            Log.i(TAG, "asking for permission for fine location");
        } else {
            Log.i(TAG, "got permission for fine location, requesting location updates");
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        }

    }

    public int addNotification(String message, KmlPlacemark placemark,
                         NotificationItem.NotificationCategory category, User user,
                               String notificationCategory) {

        NotificationItem notificationItem = new NotificationItem();
        notificationItem.setCategory(category);
        notificationItem.setMessage(message);
        notificationItem.setPlacemark(placemark == null ?
                locationBoard.getCurrentPlacemark() : placemark);
        notificationItem.setUser(user == null ? this.user : user);
        Log.d(TAG, notificationItem.toString());
        notifications.add(notificationItem);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this, getString(R.string.channel_id_important))
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle("Skigoggles2 Notification")
                        .setContentText(message)
                        .setGroup(getString(R.string.notification_group_notification));
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        int notificationId = random.nextInt();
        if(notificationCategory != null) {
            if(notificationRegistry.get(notificationCategory) == null) {
                notificationRegistry.put(notificationCategory, notificationId);
            } else {
                notificationId = notificationRegistry.get(notificationCategory);
            }
        }
        notificationManager.notify(notificationId, mBuilder.build());
        return notificationId;

    }

    public int addNotification(String message) {
        return addNotification(message, null,
                NotificationItem.NotificationCategory.INFO, null, null);
    }

    public int addNotification(String message, String notificationCategory) {
        return addNotification(message, null,
                NotificationItem.NotificationCategory.INFO, null, notificationCategory);
    }

    public int addErrorNotification(String message) {
        return addNotification(message, null,
                NotificationItem.NotificationCategory.ERROR, null, null);
    }

    public int addErrorNotification(String message, String notificationCategory) {
        return addNotification(message, null,
                NotificationItem.NotificationCategory.ERROR, null, notificationCategory);
    }

    private void addNotificationApproachingNextNode() {

        if(shortestRoute == null) {
            return;
        }

        LatLng myLatLng = new LatLng(locationBoard.getLocation().getLatitude(),
                locationBoard.getLocation().getLongitude());
        shortestRoute.stream()
                .filter(vertex -> vertex.getPlacemark() != null)
                .filter(vertex -> vertex.getPlacemark().equals(
                        locationBoard.getCurrentPlacemark()))
                .forEach(vertex -> {
                    LatLng startPointOnRun = LocationBoard.getPoints(vertex.getPlacemark()).get(0);
                    double distanceToStart = SphericalUtil.computeDistanceBetween(
                            myLatLng, startPointOnRun);
                    if(distanceToStart < METRES_TO_CHECK_FOR_NEXT_NODE) {
                        double bearingToStart = SphericalUtil.computeHeading(
                                myLatLng, startPointOnRun);
                        addNotification(String.format(Locale.ENGLISH,
                                "Approaching start of %s (%s), %.0f %s",
                                vertex.getPlacemark().getProperty("name"),
                                vertex.getPlacemark().getProperty("description"),
                                distanceToStart, bearingToCompassPoint(bearingToStart)));
                    }
                });

    }

    public void processKmlLayer(KmlLayer layer) {
        locationBoard.getAllPlacemarks().addAll(LocationBoard.getFlatListPlacemarks(layer));
        kmlLayerAlgorithm = new KmlLayerAlgorithm(layer);
    }

    public static String formatVertexList(List<Vertex> vertices) {
        return vertices.stream()
                .map(vertex -> {
                    if(vertex.getPlacemark() != null) {
                        List<LatLng> points = LocationBoard.getPoints(vertex.getPlacemark());
                        return String.format("%s of %s (%s)",
                                vertex.getLatLng() == points.get(0) ? "start" :
                                        vertex.getLatLng() == points.get(points.size() - 1) ?
                                                "end" : "mid",
                                vertex.getPlacemark().getProperty("name"),
                                vertex.getPlacemark().getProperty("description"));
                    } else {
                        return "(unnamed)";
                    }
                })
                .collect(Collectors.joining(" -> "));
    }

    public double getCostOfRoute(final List<Vertex> route) {

        List<Edge> edges = kmlLayerAlgorithm.getGraph().getEdges();
        double cost = 0d;
        for(int index = 0; index < route.size() - 1; index++) {
            final Vertex sourceVertex = route.get(index);
            final Vertex targetVertex = route.get(index + 1);
            final Optional<Edge> perhapsEdge = edges.stream().filter(edge ->
                    edge.getSource().equals(sourceVertex) &&
                            edge.getDestination().equals(targetVertex))
                    .min(Comparator.comparing(Edge::getWeight));
            if(perhapsEdge.isPresent()) {
                Edge edge = perhapsEdge.get();
                double edgeCost = edge.getWeight() + SECONDS_END_OF_RUN_FAFF;
                if(edge.getPlacemark() != null) {
                    if (edge.getPlacemark().getProperty("description").toLowerCase().contains("lift")) {
                        edgeCost += SECONDS_START_OF_CHAIRLIFT_FAFF;
                    }
                    Log.d(TAG, String.format("added on cost for %s (%s): %.0f",
                            edge.getPlacemark().getProperty("name"),
                            edge.getPlacemark().getProperty("description"),
                            edgeCost));
                } else {
                    Log.d(TAG, String.format("added on cost for untracked traversal: %.0f",
                            edgeCost));
                }
                cost += edgeCost;
            } else {
                throw new RuntimeException("couldn't find an edge between points in route");
            }
        }
        Log.d(TAG, String.format("total cost of route: %.0f", cost));
        return cost;

    }

    public List<Vertex> getShortestRoute(Polyline polyline) {

        String routeString = "Can't find route...";
        if (locationBoard != null) {
            Optional<List<Vertex>> perhapsRoute = locationBoard.getAllPlacemarks()
                    .stream()
                    .filter(kmlPlacemark ->
                            LocationBoard.getPoints(kmlPlacemark).equals(
                                    polyline.getPoints()))
                    .findFirst()
                    .map(kmlPlacemark -> getShortestRoute(kmlPlacemark.getProperty("name")));
            if(perhapsRoute.isPresent()) {
                shortestRoute = perhapsRoute.get();
                routeString = new StringBuilder(formatVertexList(shortestRoute))
                        .append("; ")
                        .append(String.format(Locale.ENGLISH,
                                " time: %.0f min", getCostOfRoute(shortestRoute) / 60))
                        .toString();
            }
        }
        Log.i(TAG, routeString);
        addNotification(routeString);
        return shortestRoute;

    }

    public List<Vertex> getShortestRoute(String runName) {

        Graph graph = kmlLayerAlgorithm.getGraph();

        // source vertex

        KmlPlacemark placemark = locationBoard.getCurrentPlacemark();
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

        Optional<KmlPlacemark> perhapsTargetPlacemark = locationBoard.getAllPlacemarks().stream()
                .filter(kmlPlacemark ->
                        runName.equalsIgnoreCase(kmlPlacemark.getProperty("name")))
                .findFirst();

        // assume target vertex is always present

        if(perhapsTargetPlacemark.isPresent()) {

            Vertex targetVertex = KmlLayerAlgorithm.createNewVertex(
                    LocationBoard.getPoints(perhapsTargetPlacemark.get()).get(0),
                    perhapsTargetPlacemark.get());

            // reinit graph and execute

            kmlLayerAlgorithm.execute(sourceVertex);
            List<Vertex> routes = kmlLayerAlgorithm.getPath(targetVertex);

            return routes;

        } else {

            return null;

        }

    }


}
