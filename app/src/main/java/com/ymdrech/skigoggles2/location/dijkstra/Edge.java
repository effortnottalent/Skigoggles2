package com.ymdrech.skigoggles2.location.dijkstra;

import com.google.maps.android.data.kml.KmlPlacemark;

/**
 * Created by richard.mathias on 21/02/2018.
 */

public class Edge {

    private final KmlPlacemark placemark;
    private final Vertex source;
    private final Vertex destination;
    private final double weight;

    public Edge(KmlPlacemark placemark, Vertex source, Vertex destination, double weight) {
        this.placemark = placemark;
        this.source = source;
        this.destination = destination;
        this.weight = weight;
    }

    public KmlPlacemark getPlacemark() {
        return placemark;
    }

    public Vertex getSource() {
        return source;
    }

    public Vertex getDestination() {
        return destination;
    }

    public double getWeight() {
        return weight;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Edge edge = (Edge) o;

        if (weight != edge.weight) return false;
        if (placemark != null ? !placemark.equals(edge.placemark) : edge.placemark != null) return false;
        if (source != null ? !source.equals(edge.source) : edge.source != null) return false;
        return destination != null ? destination.equals(edge.destination) : edge.destination == null;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = placemark != null ? placemark.hashCode() : 0;
        result = 31 * result + (source != null ? source.hashCode() : 0);
        result = 31 * result + (destination != null ? destination.hashCode() : 0);
        temp = Double.doubleToLongBits(weight);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "Edge{" +
                "placemark=" + placemark +
                ", source=" + source +
                ", destination=" + destination +
                ", weight=" + weight +
                '}';
    }
}
