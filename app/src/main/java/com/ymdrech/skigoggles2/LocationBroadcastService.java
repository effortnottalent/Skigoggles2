package com.ymdrech.skigoggles2;

import android.app.Activity;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.ymdrech.skigoggles2.location.LocationBoard;
import com.ymdrech.skigoggles2.location.Party;
import com.ymdrech.skigoggles2.utils.Maps;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Created by richard.mathias on 02/03/2018.
 */

public class LocationBroadcastService {

    private final String TAG = getClass().getCanonicalName();
    private static final int SLEEP_BETWEEN_LOCATION_RETRIEVALS = 1000;

    private User user;
    private Party party;
    private Activity activity;
    private Map<String, LocationBroadcastDto> locationRegistry = new HashMap<>();
    private RequestQueue queue;

    public Map<String, LocationBroadcastDto> getLocationRegistry() {
        return locationRegistry;
    }

    public LocationBroadcastService(User user, Party party, Activity activity) {
        this.party = party;
        this.activity = activity;
        queue = Volley.newRequestQueue(activity);
        new Thread(() -> {
            while(true) {
                receiveLocations();
                try {
                    Thread.sleep(SLEEP_BETWEEN_LOCATION_RETRIEVALS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();
    }

    public void broadcastLocation(User user, LocationBoard locationBoard) {

        LocationBroadcastDto dto = getDtoFromUserAndLocationBoard(user, locationBoard);

        String endpoint = activity.getResources()
                .getText(R.string.location_broadcast_endpoint).toString();

        StringRequest request = new StringRequest(Request.Method.POST,
                endpoint, response -> {
            Log.d(TAG, String.format("got response from POST: %s", response));
        }, error -> {
            Log.w(TAG, String.format("got error from POST: %s", error));
            Toast.makeText(activity, "Couldn't update location, check network...",
                    Toast.LENGTH_SHORT).show();
        }) {
            @Override
            public byte[] getBody() throws AuthFailureError {
                try {
                    String jsonString = getJsonFromDto(dto).toString();
                    Log.d(TAG, String.format("sending %s to endpoint %s",
                            jsonString, endpoint));
                    return jsonString.getBytes();
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
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

        String endpoint = activity.getResources()
                .getText(R.string.location_broadcast_endpoint).toString();

        StringRequest request = new StringRequest(Request.Method.GET,
                endpoint, response -> {
            try {
                Log.d(TAG, String.format("got response from GET: %s", response));
                getDtoListFromJsonArray(new JSONObject(new String(response.getBytes()))).forEach(
                        dto -> locationRegistry.put(dto.getUserId(), dto));
                Log.d(TAG, "updated registry");
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }, error -> {
            Log.w(TAG, String.format("got error from POST: %s", error));
            Toast.makeText(activity, "Couldn't retrieve party locations, check network...",
                    Toast.LENGTH_SHORT).show();
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

    List<LocationBroadcastDto> getDtoListFromJsonArray(JSONObject jsonObject) {

        List<LocationBroadcastDto> dtoList = new ArrayList<>();
        try {
            JSONArray locations = jsonObject.getJSONArray("locations");
            for (int index = 0; index < locations.length(); index++) {
                LocationBroadcastDto dto = getDtoFromJson(locations.getJSONObject(index));
                dtoList.add(dto);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return dtoList;

    }

    JSONObject getJsonFromDto(LocationBroadcastDto dto) throws JSONException {

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("user_id", dto.getUserId());
        jsonObject.put("user_name", dto.getUsername());
        jsonObject.put("date", dto.getDate());
        jsonObject.put("longitude", dto.getLongitude());
        jsonObject.put("latitude", dto.getLatitude());
        jsonObject.put("altitude", dto.getAltitude());
        jsonObject.put("accuracy", dto.getAccuracy());
        jsonObject.put("bearing", dto.getBearing());
        jsonObject.put("nearest_run_name", dto.getRunName());
        jsonObject.put("nearest_run_description", dto.getRunType());
        jsonObject.put("party", party.getId().toString());
        return jsonObject;

    }

    LocationBroadcastDto getDtoFromJson(JSONObject jsonObject) {

        try {
            LocationBroadcastDto dto = new LocationBroadcastDto();
            dto.setUsername(jsonObject.getString("user_name"));
            dto.setUserId(jsonObject.getString("user_id"));
            dto.setDate(DateFormat.getDateTimeInstance().parse(
                    jsonObject.getString("date")));
            dto.setLongitude(jsonObject.getDouble("longitude"));
            dto.setLatitude(jsonObject.getDouble("latitude"));
            dto.setAltitude(jsonObject.getDouble("altitude"));
            dto.setAccuracy(jsonObject.getDouble("accuracy"));
            dto.setBearing(jsonObject.getDouble("bearing"));
            dto.setRunName(jsonObject.getString("nearest_run_name"));
            dto.setRunType(jsonObject.getString("nearest_run_type"));
            dto.setPartyId(jsonObject.getString("party"));
            return dto;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    LocationBroadcastDto getDtoFromUserAndLocationBoard(User user, LocationBoard locationBoard) {

        LocationBroadcastDto dto = new LocationBroadcastDto();
        dto.setUsername(user.getName());
        dto.setDate(new Date());
        dto.setLongitude(locationBoard.getLocation().getLongitude());
        dto.setLatitude(locationBoard.getLocation().getLatitude());
        dto.setAltitude(locationBoard.getLocation().getAltitude());
        dto.setAccuracy(locationBoard.getLocation().getAccuracy());
        dto.setBearing(locationBoard.getLocation().getBearing());
        dto.setRunName(locationBoard.getPlacemark().getProperty("name"));
        dto.setRunType(locationBoard.getPlacemark().getProperty("description"));
        dto.setPartyId(party.getId().toString());
        return dto;

    }

}
