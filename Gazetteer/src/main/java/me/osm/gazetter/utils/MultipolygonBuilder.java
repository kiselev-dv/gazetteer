// License: GPL. For details, see LICENSE file.
package me.osm.gazetter.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

/**
 * Helper class to build multipolygons from multiple ways.
 * @author viesturs
 * @since 7392 (rename)
 * @since 3704
 */
public class MultipolygonBuilder {
	
	private static GeometryFactory factory = new GeometryFactory();

    /**
     * Represents one polygon that consists of multiple ways.
     */
    public static class JoinedPolygon {
        public final List<LineString> ways;
        public final List<Boolean> reversed;
        public final List<Point> nodes;
        public final Polygon area;
        public final Envelope bounds;

        /**
         * Constructs a new {@code JoinedPolygon} from given list of ways.
         * @param ways The ways used to build joined polygon
         */
        public JoinedPolygon(List<LineString> ways, List<Boolean> reversed) {
            this.ways = ways;
            this.reversed = reversed;
            this.nodes = this.getNodes();
            this.area = factory.createPolygon(factory.createLinearRing(this.nodesAsCoords()));
            this.bounds = area.getEnvelopeInternal();
        }
        
        private Coordinate[] nodesAsCoords() {
        	Coordinate[] coords = new Coordinate[this.nodes.size()];
        	int i =0;
        	for(Point p : this.nodes) {
        		coords[i] = new Coordinate(p.getX(), p.getY());
        	}
        	
        	return coords;
        }

        /**
         * Creates a polygon from single way.
         * @param way the way to form the polygon
         */
        public JoinedPolygon(LineString way) {
            this(Collections.singletonList(way), Collections.singletonList(Boolean.FALSE));
        }

        /**
         * Builds a list of nodes for this polygon. First node is not duplicated as last node.
         * @return list of nodes
         */
        public List<Point> getNodes() {
            List<Point> nodes = new ArrayList<>();

            for (int waypos = 0; waypos < this.ways.size(); waypos ++) {
                LineString way = this.ways.get(waypos);
                boolean reversed = this.reversed.get(waypos).booleanValue();

                if (!reversed) {
                    for (int pos = 0; pos < way.getNumPoints() - 1; pos++) {
                        nodes.add(way.getPointN(pos));
                    }
                } else {
                    for (int pos = way.getNumPoints() - 1; pos > 0; pos--) {
                        nodes.add(way.getPointN(pos));
                    }
                }
            }

            return nodes;
        }
    }

    /**
     * Helper storage class for finding findOuterWays
     */
    static class PolygonLevel {
        public final int level; //nesting level , even for outer, odd for inner polygons.
        public final JoinedPolygon outerWay;

        public List<JoinedPolygon> innerWays;

        public PolygonLevel(JoinedPolygon pol, int level) {
            this.outerWay = pol;
            this.level = level;
            this.innerWays = new ArrayList<>();
        }
    }

    /** List of outer ways **/
    public final List<JoinedPolygon> outerWays;
    /** List of inner ways **/
    public final List<JoinedPolygon> innerWays;

    /**
     * Constructs a new {@code MultipolygonBuilder} initialized with given ways.
     * @param outerWays The outer ways
     * @param innerWays The inner ways
     */
    public MultipolygonBuilder(List<JoinedPolygon> outerWays, List<JoinedPolygon> innerWays) {
        this.outerWays = outerWays;
        this.innerWays = innerWays;
    }

    /**
     * Constructs a new empty {@code MultipolygonBuilder}.
     */
    public MultipolygonBuilder() {
        this.outerWays = new ArrayList<>(0);
        this.innerWays = new ArrayList<>(0);
    }

    /**
     * Splits ways into inner and outer JoinedWays. Sets {@link #innerWays} and {@link #outerWays} to the result.
     * TODO: Currently cannot process touching polygons. See code in JoinAreasAction.
     * @param ways ways to analyze
     * @return error description if the ways cannot be split, {@code null} if all fine.
     */
    public String makeFromWays(Collection<LineString> ways) {
        try {
            List<JoinedPolygon> joinedWays = joinWays(ways);
            //analyze witch way is inside witch outside.
            return makeFromPolygons(joinedWays);
        } catch (JoinedPolygonCreationException ex) {
            return ex.getMessage();
        }
    }

    /**
     * An exception indicating an error while joining ways to multipolygon rings.
     */
    public static class JoinedPolygonCreationException extends RuntimeException {
        
		private static final long serialVersionUID = 2418483422593897394L;

		/**
         * Constructs a new {@code JoinedPolygonCreationException}.
         * @param message the detail message. The detail message is saved for
         *                later retrieval by the {@link #getMessage()} method
         */
        public JoinedPolygonCreationException(String message) {
            super(message);
        }
    }

    /**
     * Joins the given {@code ways} to multipolygon rings.
     * @param ways the ways to join.
     * @return a list of multipolygon rings.
     * @throws JoinedPolygonCreationException if the creation fails.
     */
    public static List<JoinedPolygon> joinWays(Collection<LineString> ways) throws JoinedPolygonCreationException {
        List<JoinedPolygon> joinedWays = new ArrayList<>();

        //collect ways connecting to each node.
        MultiMap<Point, LineString> nodesWithConnectedWays = new MultiMap<>();
        Set<LineString> usedWays = new HashSet<>();

        for (LineString w: ways) {
            if (w.getNumPoints() < 2) {
                //log("Cannot add a way with only {} nodes.", w.getNumPoints()));
            }

            if (w.isClosed()) {
                //closed way, add as is.
                JoinedPolygon jw = new JoinedPolygon(w);
                joinedWays.add(jw);
                usedWays.add(w);
            } else {
                nodesWithConnectedWays.put(w.getEndPoint(), w);
                nodesWithConnectedWays.put(w.getStartPoint(), w);
            }
        }

        //process unclosed ways
        for (LineString startWay: ways) {
            if (usedWays.contains(startWay)) {
                continue;
            }

            Point startNode = startWay.getStartPoint();
            List<LineString> collectedWays = new ArrayList<>();
            List<Boolean> collectedWaysReverse = new ArrayList<>();
            LineString curWay = startWay;
            Point prevNode = startNode;

            //find polygon ways
            while (true) {
                boolean curWayReverse = prevNode == curWay.getEndPoint();
                Point nextNode = (curWayReverse) ? curWay.getStartPoint(): curWay.getEndPoint();

                //add cur way to the list
                collectedWays.add(curWay);
                collectedWaysReverse.add(Boolean.valueOf(curWayReverse));

                if (nextNode == startNode) {
                    //way finished
                    break;
                }

                //find next way
                Collection<LineString> adjacentWays = nodesWithConnectedWays.get(nextNode);

                if (adjacentWays.size() != 2) {
                    //throw new JoinedPolygonCreationException(tr("Each node must connect exactly 2 ways"));
                }

                LineString nextWay = null;
                for(LineString way: adjacentWays) {
                    if (way != curWay) {
                        nextWay = way;
                    }
                }

                //move to the next way
                curWay = nextWay;
                prevNode = nextNode;
            }

            usedWays.addAll(collectedWays);
            joinedWays.add(new JoinedPolygon(collectedWays, collectedWaysReverse));
        }

        return joinedWays;
    }

    /**
     * This method analyzes which ways are inner and which outer. Sets {@link #innerWays} and {@link #outerWays} to the result.
     * @param polygons polygons to analyze
     * @return error description if the ways cannot be split, {@code null} if all fine.
     */
    private String makeFromPolygons(List<JoinedPolygon> polygons) {
        List<PolygonLevel> list = findOuterWaysMultiThread(polygons);

        if (list == null) {
            return "There is an intersection between ways.";
        }

        this.outerWays.clear();
        this.innerWays.clear();

        //take every other level
        for (PolygonLevel pol : list) {
            if (pol.level % 2 == 0) {
                this.outerWays.add(pol.outerWay);
            } else {
                this.innerWays.add(pol.outerWay);
            }
        }

        return null;
    }

    private static Pair<Boolean, List<JoinedPolygon>> findInnerWaysCandidates(JoinedPolygon outerWay, Collection<JoinedPolygon> boundaryWays) {
        boolean outerGood = true;
        List<JoinedPolygon> innerCandidates = new ArrayList<>();

        for (JoinedPolygon innerWay : boundaryWays) {
            if (innerWay == outerWay) {
                continue;
            }

            // Preliminary computation on bounds. If bounds do not intersect, no need to do a costly area intersection
            if (outerWay.bounds.intersects(innerWay.bounds)) {
                // Bounds intersection, let's see in detail
                PolygonIntersection intersection = polygonIntersection(outerWay.area, innerWay.area, 1.0);

                if (intersection == PolygonIntersection.FIRST_INSIDE_SECOND) {
                    outerGood = false;  // outer is inside another polygon
                    break;
                } else if (intersection == PolygonIntersection.SECOND_INSIDE_FIRST) {
                    innerCandidates.add(innerWay);
                } else if (intersection == PolygonIntersection.CROSSING) {
                    // ways intersect
                    return null;
                }
            }
        }

        return new Pair<>(outerGood, innerCandidates);
    }

    /**
     * Collects outer way and corresponding inner ways from all boundaries.
     * @return the outermostWay, or {@code null} if intersection found.
     */
    private static List<PolygonLevel> findOuterWaysMultiThread(List<JoinedPolygon> boundaryWays) {
        final List<PolygonLevel> result = new ArrayList<>();
        final List<Worker> tasks = new ArrayList<>();
        final int bucketsize = Math.max(32, boundaryWays.size()/3);
        final int noBuckets = (boundaryWays.size() + bucketsize - 1) / bucketsize;
        final boolean singleThread = true;
        for (int i=0; i<noBuckets; i++) {
            int from = i*bucketsize;
            int to = Math.min((i+1)*bucketsize, boundaryWays.size());
            List<PolygonLevel> target = singleThread ? result : new ArrayList<PolygonLevel>(to - from);
            tasks.add(new Worker(boundaryWays, from, to, target));
        }
        if (singleThread) {
            try {
                for (Worker task : tasks) {
                    if (task.call() == null) {
                        return null;
                    }
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        return result;
    }

    private static class Worker implements Callable<List<PolygonLevel>> {

        private final List<JoinedPolygon> input;
        private final int from;
        private final int to;
        private final List<PolygonLevel> output;

        public Worker(List<JoinedPolygon> input, int from, int to, List<PolygonLevel> output) {
            this.input = input;
            this.from = from;
            this.to = to;
            this.output = output;
        }

        /**
         * Collects outer way and corresponding inner ways from all boundaries.
         * @return the outermostWay, or {@code null} if intersection found.
         */
        private static List<PolygonLevel> findOuterWaysRecursive(int level, List<JoinedPolygon> boundaryWays) {

            final List<PolygonLevel> result = new ArrayList<>();

            for (JoinedPolygon outerWay : boundaryWays) {
                if (processOuterWay(level, boundaryWays, result, outerWay) == null) {
                    return null;
                }
            }

            return result;
        }

        private static List<PolygonLevel> processOuterWay(int level, List<JoinedPolygon> boundaryWays, final List<PolygonLevel> result, JoinedPolygon outerWay) {
            Pair<Boolean, List<JoinedPolygon>> p = findInnerWaysCandidates(outerWay, boundaryWays);
            if (p == null) {
                // ways intersect
                return null;
            }

            if (p.a) {
                //add new outer polygon
                PolygonLevel pol = new PolygonLevel(outerWay, level);

                //process inner ways
                if (!p.b.isEmpty()) {
                    List<PolygonLevel> innerList = findOuterWaysRecursive(level + 1, p.b);
                    if (innerList == null) {
                        return null; //intersection found
                    }

                    result.addAll(innerList);

                    for (PolygonLevel pl : innerList) {
                        if (pl.level == level + 1) {
                            pol.innerWays.add(pl.outerWay);
                        }
                    }
                }

                result.add(pol);
            }
            return result;
        }

        @Override
        public List<PolygonLevel> call() throws Exception {
            for (int i = from; i<to; i++) {
                if (processOuterWay(0, input, output, input.get(i)) == null) {
                    return null;
                }
            }
            return output;
        }
    }
    
    public enum PolygonIntersection {FIRST_INSIDE_SECOND, SECOND_INSIDE_FIRST, OUTSIDE, CROSSING}
    
    /**
     * Tests if two polygons intersect.
     * @param a1 Area of first polygon
     * @param a2 Area of second polygon
     * @param eps an area threshold, everything below is considered an empty intersection
     * @return intersection kind
     */
    public static PolygonIntersection polygonIntersection(Polygon a1, Polygon a2, double eps) {

        com.vividsolutions.jts.geom.Geometry inter = a1.intersection(a2);

        Envelope bounds = inter.getEnvelopeInternal();

        if (inter.isEmpty() || bounds.getHeight()*bounds.getWidth() <= eps) {
            return PolygonIntersection.OUTSIDE;
        } else if (inter.equals(a1)) {
            return PolygonIntersection.FIRST_INSIDE_SECOND;
        } else if (inter.equals(a2)) {
            return PolygonIntersection.SECOND_INSIDE_FIRST;
        } else {
            return PolygonIntersection.CROSSING;
        }
    }
}
