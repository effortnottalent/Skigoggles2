package com.ymdrech.skigoggles2.location.dijkstra;

import android.graphics.Color;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;
import com.google.android.gms.maps.model.SquareCap;
import com.google.maps.android.SphericalUtil;
import com.google.maps.android.data.kml.KmlLayer;
import com.google.maps.android.data.kml.KmlPlacemark;
import com.ymdrech.skigoggles2.location.LocationBoard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.ymdrech.skigoggles2.utils.Maps.*;

/**
 * Created by richard.mathias on 20/02/2018.
 */

public class KmlLayerAlgorithm extends Algorithm {

    private KmlLayer kmlLayer;

    private Set<Vertex> vertices = new HashSet<>();
    private Set<Edge> edges = new HashSet<>();

    private List<Polyline> polylineList = new ArrayList<>();

    private static final List<String> RUN_DESCRIPTIONS =
            Arrays.asList("red", "green", "blue", "black");

    private static final Map<String, Double> SPEEDS =
            Collections.unmodifiableMap(Stream.of(
            entry("chairlift", 3d),
            entry("button lift", 3d),
            entry("drag lift", 3d),
            entry("gondola", 3d),
            entry("cable car", 3d),
            entry("green", 10d),
            entry("red", 20d),
            entry("black", 20d),
            entry("blue", 20d)).collect(entriesToMap()));

    private static final Double SPEED_WALKING = 1d;
    private static final Double TIME_MAX = 300d;
    private static final Double METRES_OFF_END_OF_RUN_TO_OTHER_RUN = 50d;

    public Graph createGraph(KmlLayer kmlLayer) {

        vertices = new HashSet<>();
        edges = new HashSet<>();

        List<KmlPlacemark> placemarks = LocationBoard.getFlatListPlacemarks(kmlLayer);
        placemarks.forEach(placemark -> {
            List<LatLng> points = LocationBoard.getPoints(placemark);
            if(points.size() > 0) {

                // add vertices and edges to lists from placemark

                Vertex startPoint = createNewVertex(points.get(0), placemark);
                Vertex endPoint = createNewVertex(points.get(points.size() - 1), placemark);
                vertices.add(startPoint);
                vertices.add(endPoint);
                edges.add(createNewEdge(startPoint, endPoint, placemark));

                placemarks.stream().filter(otherPlacemark -> !otherPlacemark.equals(placemark))
                        .forEach(otherPlacemark -> {
                    List<LatLng> otherPoints = LocationBoard.getPoints(otherPlacemark);
                    if(otherPoints.size() > 0) {

                    }
                });

                placemarks.stream()
                        .filter(otherPlacemark -> !otherPlacemark.equals(placemark))
                        .forEach(otherPlacemark -> {

                    List<LatLng> otherPoints = LocationBoard.getPoints(otherPlacemark);
                    if(otherPoints.size() > 0) {

                        Vertex otherStartPoint = createNewVertex(otherPoints.get(0), otherPlacemark);
                        Vertex otherEndPoint = createNewVertex(otherPoints.get(otherPoints.size() - 1),
                                otherPlacemark);

                        if(RUN_DESCRIPTIONS.contains(
                                otherPlacemark.getProperty("description").toLowerCase())) {

                            // add vertex and edge to join end of our run to middle of nearest run

                            LatLng closestPointOnOtherRunFromOurEnd = otherPoints.stream()
                                    .min(Comparator.comparing(point ->
                                            SphericalUtil.computeDistanceBetween(endPoint.getLatLng(),
                                                    point)))
                                    .get();
                            if (SphericalUtil.computeDistanceBetween(endPoint.getLatLng(),
                                    closestPointOnOtherRunFromOurEnd) < METRES_OFF_END_OF_RUN_TO_OTHER_RUN) {
                                Vertex newMidPoint = createNewVertex(closestPointOnOtherRunFromOurEnd,
                                        otherPlacemark);
                                vertices.add(newMidPoint);
                                edges.add(createNewEdge(endPoint, newMidPoint, null));
                                edges.add(createNewEdge(newMidPoint, otherEndPoint, otherPlacemark));
                            }

                            // add vertex and edge to join start of nearest run to middle of our run

                            LatLng closestPointOnOurRunFromOtherStart = points.stream()
                                    .min(Comparator.comparing(point ->
                                            SphericalUtil.computeDistanceBetween(otherStartPoint.getLatLng(),
                                                    point)))
                                    .get();

                            if (SphericalUtil.computeDistanceBetween(otherStartPoint.getLatLng(),
                                    closestPointOnOurRunFromOtherStart) < METRES_OFF_END_OF_RUN_TO_OTHER_RUN) {
                                Vertex newMidPoint = createNewVertex(closestPointOnOurRunFromOtherStart,
                                        placemark);
                                vertices.add(newMidPoint);
                                edges.add(createNewEdge(startPoint, newMidPoint, placemark));
                                edges.add(createNewEdge(newMidPoint, otherStartPoint, null));
                            }

                        }

                        // add edges from start and end to other starts and ends, to traverse to other runs

                        vertices.add(otherStartPoint);
                        vertices.add(otherEndPoint);
                        edges.add(createNewEdge(startPoint, otherStartPoint, null));
                        edges.add(createNewEdge(startPoint, otherEndPoint, null));
                        edges.add(createNewEdge(endPoint, otherStartPoint, null));
                        edges.add(createNewEdge(endPoint, otherEndPoint, null));

                    }
                });

            }
        });
        vertices.remove(null);
        edges.remove(null);

        return new Graph(new ArrayList<>(vertices), new ArrayList<>(edges));

    }

    public static Edge createNewEdge(Vertex startPoint, Vertex endPoint, KmlPlacemark placemark) {

        if(startPoint.equals(endPoint)) {
            return null;
        }
        double distance;
        if(placemark != null) {
            final List<LatLng> points = LocationBoard.getPoints(placemark);
            final List<LatLng> edgePoints = points.stream()
                    .filter(point -> points.indexOf(point) >= points.indexOf(startPoint.getLatLng()) &&
                            points.indexOf(point) <= points.indexOf(endPoint.getLatLng()))
                    .collect(Collectors.toList());
            distance = SphericalUtil.computeLength(edgePoints);
        } else {
            distance = SphericalUtil.computeDistanceBetween(startPoint.getLatLng(), endPoint.getLatLng());
        }
        double speed = placemark != null ? SPEEDS.getOrDefault(
                placemark.getProperty("description").toLowerCase(),
                5d) : SPEED_WALKING;
        double edgeTime = distance / speed;
        Edge edge = new Edge(placemark, startPoint, endPoint, edgeTime);
        if(placemark != null || edgeTime < TIME_MAX) {
            return edge;
        } else {
            return null;
        }

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

    public static Vertex createNewVertex(LatLng point, KmlPlacemark placemark) {
        return new Vertex(point, placemark);
    }

    public void drawMap(GoogleMap googleMap) {
        polylineList.forEach(Polyline::remove);
        getGraph().getEdges().forEach(edge -> {
            Polyline polyline = googleMap.addPolyline(new PolylineOptions()
                    .color(Color.argb(100,0,255,255))
                    .width(20)
                    .zIndex(999)
                    .startCap(new RoundCap())
                    .endCap(new SquareCap())
                    .addAll(edge.getPlacemark() != null ?
                            LocationBoard.getPoints(edge.getPlacemark()) :
                            Arrays.asList(edge.getSource().getLatLng(),
                                    edge.getDestination().getLatLng())));
            polylineList.add(polyline);
        });
    }


    public KmlLayerAlgorithm(KmlLayer kmlLayer) {
        setGraph(createGraph(kmlLayer));
    }

}
