package com.ymdrech.skigoggles2.location.dijkstra;

import com.google.maps.android.data.kml.KmlPlacemark;

import lombok.Data;

/**
 * Created by richard.mathias on 21/02/2018.
 */

@Data
public class Edge {

    private final KmlPlacemark placemark;
    private final Vertex source;
    private final Vertex destination;
    private final double weight;

}
