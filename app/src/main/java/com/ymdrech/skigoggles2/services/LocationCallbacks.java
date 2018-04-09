package com.ymdrech.skigoggles2.services;

import android.location.Location;

/**
 * Created by richard.mathias on 20/03/2018.
 */

public interface LocationCallbacks {
    void receiveLocationUpdate(Location location);
}
