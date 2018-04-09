package com.ymdrech.skigoggles2.components;

import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.SphericalUtil;
import com.ymdrech.skigoggles2.R;
import com.ymdrech.skigoggles2.location.LocationBoard;
import com.ymdrech.skigoggles2.location.Party;
import com.ymdrech.skigoggles2.services.LocationService;
import com.ymdrech.skigoggles2.utils.Maps;
import com.ymdrech.skigoggles2.User;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Stream;


/**
 * Created by richard.mathias on 02/03/2018.
 */

public class LocationBroadcastComponent {

    private static final String NOTIFICATION_CONNECTIVITY = "NOTIFICATION_CONNECTIVITY";
    private static final String NOTIFICATION_PERSON_LOST_PREFIX = "NOTIFICATION_PERSON_LOST_";

    private final String TAG = getClass().getCanonicalName();
    private static final int SECONDS_SLEEP_BETWEEN_LOCATION_RETRIEVALS = 1;
    private static final int SECONDS_ALERT_UPON_STALE_LOCATION = 60;
    private static final int SECONDS_SIZE_OF_GROUP = 20;
    private static final int METRES_MINIMUM_SIZE_OF_GROUP = 20;

    private User user;
    private Party party;
    private LocationService locationService;
    private Map<String, LocationBroadcastDto> locationRegistry = new HashMap<>();
    private RequestQueue queue;
    private Set<String> usersLeftGroup = new HashSet<>();
    private boolean connectedToRemote = true;

    private static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    public Map<String, LocationBroadcastDto> getLocationRegistry() {
        return locationRegistry;
    }

    public LocationBroadcastComponent(User user, Party party, LocationService locationService) {
        this.party = party;
        this.locationService = locationService;
        this.user = user;
        queue = Volley.newRequestQueue(locationService);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        new Thread(() -> {
            while(true) {
                receiveLocations();
                runLocationChecks();
                try {
                    Thread.sleep(SECONDS_SLEEP_BETWEEN_LOCATION_RETRIEVALS * 1000);
                } catch (InterruptedException e) {
                    Log.w(TAG, "got interrupted during sleep", e);
                }
            }
        }).start();
    }

    public void broadcastLocation(User user, LocationBoard locationBoard) {

        LocationBroadcastDto dto = getDtoFromUserAndLocationBoard(user, locationBoard);

        String endpoint = locationService.getResources()
                .getText(R.string.location_broadcast_endpoint).toString();

        StringRequest request = new StringRequest(Request.Method.POST,
                endpoint, response -> {
            Log.d(TAG, String.format("got response from POST: %s", response));
            synchronized (LocationBroadcastComponent.class) {
                if (!connectedToRemote) {
                    locationService.addNotification("Reconnected to remote service",
                            NOTIFICATION_CONNECTIVITY);
                    connectedToRemote = true;
                }
            }
        }, error -> {
            Log.w(TAG, "got error from POST", error);
            synchronized (LocationBroadcastComponent.class) {
                if (connectedToRemote) {
                    locationService.addErrorNotification(
                            "Couldn't update location, check network...",
                            NOTIFICATION_CONNECTIVITY);
                    connectedToRemote = false;
                }
            }
        }) {
            @Override
            public byte[] getBody() throws AuthFailureError {
                String jsonString = getJsonFromDto(dto).toString();
                Log.d(TAG, String.format("sending %s to endpoint %s",
                        jsonString, endpoint));
                return jsonString.getBytes();
            }

            @Override
            public String getBodyContentType() {
                return "application/json";
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return Collections.unmodifiableMap(Stream.of(
                        Maps.entry("Content-Type", "application/json"),
                        Maps.entry("Accept", "application/json")
                ).collect(Maps.entriesToMap()));
            }

        };
        queue.add(request);

    }

    public void receiveLocations() {

        String endpoint = locationService.getResources()
                .getText(R.string.location_broadcast_endpoint).toString();

        StringRequest request = new StringRequest(Request.Method.GET,
                endpoint, response -> {
            Log.d(TAG, String.format("got response from GET: %s", response));
            getDtoListFromJsonArray(new String(response.getBytes())).forEach(
                    dto -> locationRegistry.put(dto.getUserId(), dto));
            Log.d(TAG, "updated registry");
        }, error -> {
            Log.w(TAG, "got error from GET", error);
            locationService.addErrorNotification("Couldn't update location, check network...",
                    NOTIFICATION_CONNECTIVITY);
        }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return Collections.unmodifiableMap(Stream.of(
                        Maps.entry("Content-Type", "application/json"),
                        Maps.entry("Accept", "application/json")
                ).collect(Maps.entriesToMap()));
            }
        };
        queue.add(request);

    }

    public void runLocationChecks() {

        Date date = new Date();
        LocationBroadcastDto myDto = locationRegistry.get(user.getId());
        if(myDto != null) {
            LatLng myLatLng = new LatLng(myDto.getLatitude(), myDto.getLongitude());
            locationRegistry.values().stream()
                    .filter(dto -> !dto.equals(myDto))
                    .forEach(dto -> {
                        if ((date.getTime() - dto.getDate().getTime()) / 1000 >
                                SECONDS_ALERT_UPON_STALE_LOCATION) {
                            locationService.addNotification(String.format(
                                    "Haven't received a location entry for %s since %s, last seen on %s (%s)",
                                    dto.getUsername(), dto.getDate(), dto.getRunName(), dto.getRunType()),
                                    NOTIFICATION_PERSON_LOST_PREFIX + dto.getUsername());
                            usersLeftGroup.add(dto.getUserId());
                        } else {
                            double distanceInMetres = SphericalUtil.computeDistanceBetween(myLatLng,
                                    new LatLng(dto.getLatitude(), dto.getLongitude()));
                            if (distanceInMetres > METRES_MINIMUM_SIZE_OF_GROUP) {
                                double relativeSpeed = myDto.getSpeed() -
                                        dto.getSpeed() * Math.cos(Math.toRadians(
                                                dto.getBearing() - myDto.getBearing()));
                                double distanceInSeconds = distanceInMetres / Math.abs(relativeSpeed);
                                if (distanceInSeconds > SECONDS_SIZE_OF_GROUP) {
                                    locationService.addNotification(String.format(Locale.ENGLISH,
                                            "%s is more than %.0f seconds away on %s (%s)",
                                            dto.getUsername(), distanceInSeconds, dto.getRunName(),
                                            dto.getRunType()),
                                            NOTIFICATION_PERSON_LOST_PREFIX + dto.getUsername());
                                    usersLeftGroup.add(dto.getUserId());
                                }
                            } else {
                                if (usersLeftGroup.contains(dto.getUserId())) {
                                    locationService.addNotification(String.format("%s is back with the group",
                                            dto.getUsername()),
                                            NOTIFICATION_PERSON_LOST_PREFIX + dto.getUsername());
                                    usersLeftGroup.remove(dto.getUserId());
                                }
                            }
                        }
                    });
        }

    }

    public List<LocationBroadcastDto> getDtoListFromJsonArray(String json) {

        List<LocationBroadcastDto> dtoList = new ArrayList<>();
        try {
            JSONObject jsonObject = new JSONObject(json);
            JSONArray locations = jsonObject.getJSONArray("locations");
            for (int index = 0; index < locations.length(); index++) {
                LocationBroadcastDto dto = getDtoFromJson(locations.getJSONObject(index));
                if(dto != null) {
                    dtoList.add(dto);
                }
            }
        } catch (JSONException e) {
            Log.w(TAG, "problems getting dto list from json", e);
        }
        return dtoList;

    }

    JSONObject getJsonFromDto(LocationBroadcastDto dto) {

        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("userId", dto.getUserId());
            jsonObject.put("username", dto.getUsername());
            jsonObject.put("date", dto.getDate().getTime());
            jsonObject.put("longitude", dto.getLongitude());
            jsonObject.put("latitude", dto.getLatitude());
            jsonObject.put("altitude", dto.getAltitude());
            jsonObject.put("accuracy", dto.getAccuracy());
            jsonObject.put("bearing", dto.getBearing());
            jsonObject.put("runName", dto.getRunName());
            jsonObject.put("runType", dto.getRunType());
            jsonObject.put("speed", dto.getSpeed());
            jsonObject.put("partyId", party.getId());
            return jsonObject;
        } catch (JSONException e) {
            Log.w(TAG, "Couldn't create JSON", e);
            return null;
        }

    }

    LocationBroadcastDto getDtoFromJson(JSONObject jsonObject) {

        try {
            LocationBroadcastDto dto = new LocationBroadcastDto();
            dto.setUsername(jsonObject.getString("username"));
            dto.setUserId(jsonObject.getString("userId"));
            dto.setDate(dateFormat.parse(jsonObject.getString("date")));
            dto.setLongitude(jsonObject.getDouble("longitude"));
            dto.setLatitude(jsonObject.getDouble("latitude"));
            dto.setAltitude(jsonObject.getDouble("altitude"));
            dto.setAccuracy(jsonObject.getDouble("accuracy"));
            dto.setBearing(jsonObject.getDouble("bearing"));
            dto.setRunName(jsonObject.getString("runName"));
            dto.setRunType(jsonObject.getString("runType"));
            dto.setSpeed(jsonObject.getDouble("speed"));
            dto.setPartyId(jsonObject.getString("partyId"));
            return dto;
        } catch (JSONException | ParseException e) {
            Log.w(TAG, "Couldn't parse JSON", e);
            return null;
        }
    }

    LocationBroadcastDto getDtoFromUserAndLocationBoard(User user, LocationBoard locationBoard) {

        LocationBroadcastDto dto = new LocationBroadcastDto();
        dto.setUsername(user.getName());
        dto.setUserId(user.getId());
        dto.setDate(new Date());
        dto.setLongitude(locationBoard.getLocation().getLongitude());
        dto.setLatitude(locationBoard.getLocation().getLatitude());
        dto.setAltitude(locationBoard.getLocation().getAltitude());
        dto.setAccuracy(locationBoard.getLocation().getAccuracy());
        dto.setBearing((locationBoard.getLocation().hasBearing() &&
                locationBoard.getLocation().getBearing() != 0) ?
                locationBoard.getLocation().getBearing() : locationBoard.getCalculatedBearing());
        dto.setRunName(locationBoard.getCurrentPlacemark().getProperty("name"));
        dto.setRunType(locationBoard.getCurrentPlacemark().getProperty("description"));
        dto.setSpeed((locationBoard.getLocation().hasSpeed() &&
                locationBoard.getLocation().getSpeed() != 0) ?
                locationBoard.getLocation().getSpeed() : locationBoard.getCalculatedSpeed());
        dto.setPartyId(party.getId());
        return dto;

    }

}
