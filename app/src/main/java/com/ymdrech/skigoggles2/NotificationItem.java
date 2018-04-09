package com.ymdrech.skigoggles2;

import com.google.maps.android.data.kml.KmlPlacemark;

import java.util.Date;

import lombok.Data;

/**
 * Created by richard.mathias on 13/03/2018.
 */

@Data
public class NotificationItem {

    private User user;
    private KmlPlacemark placemark;
    private Date date = new Date();
    private String message = "No message specified.";
    private boolean displayed;
    private NotificationCategory category = NotificationCategory.INFO;

    public enum NotificationCategory {
        INFO, WARNING, ERROR;
    }

}
