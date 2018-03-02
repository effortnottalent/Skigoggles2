package com.ymdrech.skigoggles2;

import android.app.Activity;
import android.content.res.AssetManager;
import android.util.JsonWriter;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.ymdrech.skigoggles2.location.LocationBoard;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.Map;

/**
 * Created by richard.mathias on 02/03/2018.
 */

public class LocationBroadcastService {

    public void broadcastToGroup(User user, LocationBoard locationBoard, Activity activity) {

        LocationBroadcastDto dto = new LocationBroadcastDto();
        dto.setUserId(user.getId());
        dto.setDate(new Date());
        dto.setLocation(locationBoard.getLocation());
        dto.setPlacemark(locationBoard.getPlacemark());

        String endpoint = activity.getResources()
                .getText(R.string.location_broadcast_endpoint).toString();

        JSONObject jsonObject = null;
        try {
            jsonObject = getJsonFromDto(dto);
        } catch (JSONException e) {
            Log.w(getClass().getCanonicalName(), "problems creating json to post", e);
            return;
        }

        Log.d(getClass().getCanonicalName(), String.format("sending %s to endpoint %s",
                jsonObject, endpoint));

        StringRequest request = new StringRequest(Request.Method.POST,
                endpoint, response -> {
            Log.d(getClass().getCanonicalName(), String.format("got response %s", response));
        }, error -> {
            Log.w(getClass().getCanonicalName(), String.format("got error %s", error));
            Toast.makeText(activity, "Couldn't update location, check network...",
                    Toast.LENGTH_SHORT).show();
        }) {
            @Override
            public byte[] getBody() throws AuthFailureError {
                try {
                    return getJsonFromDto(dto).toString().getBytes();
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public String getBodyContentType() {
                return "application/json";
            }

        };

        RequestQueue queue = Volley.newRequestQueue(activity);
        queue.add(request);

    }

    JSONObject getJsonFromDto(LocationBroadcastDto dto) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("userId", dto.getUserId());
        jsonObject.put("longitude", dto.getLocation().getLongitude());
        jsonObject.put("latitude", dto.getLocation().getLatitude());
        jsonObject.put("altitude", dto.getLocation().getAltitude());
        jsonObject.put("accuracy", dto.getLocation().getAccuracy());
        jsonObject.put("bearing", dto.getLocation().getBearing());
        jsonObject.put("nearest_run_name", dto.getPlacemark().getProperty("name"));
        jsonObject.put("nearest_run_description", dto.getPlacemark().getProperty("description"));
        return jsonObject;
    }
}
