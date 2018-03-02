package com.ymdrech.skigoggles2.location.dijkstra;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.Data;

/**
 * Created by richard.mathias on 20/02/2018.
 * With homage to http://www.baeldung.com/java-dijkstra
 */

@Data
public class Graph {

    private final List<Vertex> vertexes;
    private final List<Edge> edges;

}
