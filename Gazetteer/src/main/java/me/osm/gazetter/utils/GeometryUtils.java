package me.osm.gazetter.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.CoordinateList;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineSegment;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.util.LineStringExtracter;
import com.vividsolutions.jts.operation.polygonize.Polygonizer;

public class GeometryUtils {

	/* Most imports from vividsolutions JCS (GNU GPLv2)
	 */
	
	public static List<Polygon> splitPolygon(Polygon polygon, LineString line) {
		GeometryFactory f = new GeometryFactory();

		Geometry nodedLinework = polygon.getBoundary().union(line);
		Polygonizer polygonizer = new Polygonizer();
		polygonizer.add(LineStringExtracter.getLines(nodedLinework));
		@SuppressWarnings("unchecked")
		Collection<Polygon> polygons = polygonizer.getPolygons();

		// only keep polygons which are inside the input
		List<Polygon> output = new ArrayList<>();
		for (Polygon p : polygons) {
			if (polygon.contains(p.getInteriorPoint()))
				output.add(p);
		}

		List<Polygon> result = new ArrayList<>();

		for (Polygon outer : output) {

			Polygon r = outer;

			for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
				LineString interiorRing = polygon.getInteriorRingN(i);
				LinearRing interior = f.createLinearRing(interiorRing
						.getCoordinates());
				Polygon inner = f.createPolygon(interior);

				if (inner.intersects(outer)) {
					Polygon difference = (Polygon) outer.difference(inner);
					if (difference.isValid()) {
						r = difference;
					}
				}
			}

			result.add(r);
		}

		return result;
	}

	/**
	 * Splits the lineString at the point closest to c.
	 * 
	 * @param moveSplitToTarget
	 *            true to move the split point to the target; false to leave the
	 *            split point at the closest point on the line to the target
	 */
	public static LineString[] split(LineString lineString, Coordinate target,
			boolean moveSplitToTarget) {
		LengthToPoint lengthToPoint = new LengthToPoint(lineString, target);
		LengthSubstring lengthSubstring = new LengthSubstring(lineString);
		LineString[] newLineStrings = new LineString[] {
				lengthSubstring.getSubstring(0, lengthToPoint.getLength()),
				lengthSubstring.getSubstring(lengthToPoint.getLength(),
						lineString.getLength()) };
		if (moveSplitToTarget) {
			newLineStrings[1].getStartPoint().getCoordinate()
					.setCoordinate((Coordinate) target.clone());
		}
		// Make sure the coordinates are absolutely equal [Jon Aquino
		// 11/21/2003]
		newLineStrings[0]
				.getEndPoint()
				.getCoordinate()
				.setCoordinate(
						newLineStrings[1].getStartPoint().getCoordinate());

		return newLineStrings;
	}

	/**
	 * Computes the length along a LineString to the point on the line nearest a
	 * given point.
	 */
	private static class LengthToPoint {

		private double minDistanceToPoint;
		private double locationLength;

		public LengthToPoint(LineString line, Coordinate inputPt) {
			computeLength(line, inputPt);
		}

		public double getLength() {
			return locationLength;
		}

		private void computeLength(LineString line, Coordinate inputPt) {
			minDistanceToPoint = Double.MAX_VALUE;
			double baseLocationDistance = 0.0;
			Coordinate[] pts = line.getCoordinates();
			LineSegment seg = new LineSegment();
			for (int i = 0; i < pts.length - 1; i++) {
				seg.p0 = pts[i];
				seg.p1 = pts[i + 1];
				updateLength(seg, inputPt, baseLocationDistance);
				baseLocationDistance += seg.getLength();

			}
		}

		private void updateLength(LineSegment seg, Coordinate inputPt,
				double segStartLocationDistance) {
			double dist = seg.distance(inputPt);
			if (dist > minDistanceToPoint)
				return;
			minDistanceToPoint = dist;
			// found new minimum, so compute location distance of point
			double projFactor = seg.projectionFactor(inputPt);
			if (projFactor <= 0.0)
				locationLength = segStartLocationDistance;
			else if (projFactor <= 1.0)
				locationLength = segStartLocationDistance + projFactor
						* seg.getLength();
			else
				locationLength = segStartLocationDistance + seg.getLength();
		}
	}

	/**
	 * Computes a substring of a {@link LineString} between given distances
	 * along the line.
	 * <ul>
	 * <li>The distances are clipped to the actual line length
	 * <li>If the start distance is equal to the end distance, a zero-length
	 * line with two identical points is returned
	 * <li>FUTURE: If the start distance is greater than the end distance, an
	 * inverted section of the line is returned
	 * </ul>
	 * <p>
	 * FUTURE: should handle startLength > endLength, and flip the returned
	 * linestring. Also should handle negative lengths (they are measured from
	 * end of line backwards).
	 */
	private static class LengthSubstring {

		private LineString line;

		public LengthSubstring(LineString line) {
			this.line = line;
		}

		public LineString getSubstring(double startDistance, double endDistance) {
			// future: if start > end, flip values and return an inverted line
			assert startDistance <= endDistance : "inverted distances not currently supported";

			Coordinate[] coordinates = line.getCoordinates();
			// check for a zero-length segment and handle appropriately
			if (endDistance <= 0.0) {
				return line.getFactory().createLineString(
						new Coordinate[] { coordinates[0], coordinates[0] });
			}
			if (startDistance >= line.getLength()) {
				return line.getFactory().createLineString(
						new Coordinate[] { coordinates[coordinates.length - 1],
								coordinates[coordinates.length - 1] });
			}
			if (startDistance < 0.0) {
				startDistance = 0.0;
			}
			return computeSubstring(startDistance, endDistance);
		}

		/**
		 * Assumes input is strictly valid (e.g. startDist < endDistance)
		 * 
		 * @param startDistance
		 * @param endDistance
		 * @return
		 */
		private LineString computeSubstring(double startDistance,
				double endDistance) {
			Coordinate[] coordinates = line.getCoordinates();
			CoordinateList newCoordinates = new CoordinateList();
			double segmentStartDistance = 0.0;
			double segmentEndDistance = 0.0;

			int i = 0;
			LineSegment segment = new LineSegment();
			while (i < coordinates.length - 1
					&& endDistance > segmentEndDistance) {
				segment.p0 = coordinates[i];
				segment.p1 = coordinates[i + 1];
				i++;
				segmentStartDistance = segmentEndDistance;
				segmentEndDistance = segmentStartDistance + segment.getLength();

				if (startDistance > segmentEndDistance)
					continue;
				if (startDistance >= segmentStartDistance
						&& startDistance < segmentEndDistance) {
					newCoordinates.add(LocatePoint.pointAlongSegment(
							segment.p0, segment.p1, startDistance
									- segmentStartDistance), false);
				}
				/*
				 * if (startDistance >= segmentStartDistance && startDistance ==
				 * segmentEndDistance) { newCoordinates.add(new
				 * Coordinate(segment.p1), false); }
				 */
				if (endDistance >= segmentEndDistance) {
					newCoordinates.add(new Coordinate(segment.p1), false);
				}
				if (endDistance >= segmentStartDistance
						&& endDistance < segmentEndDistance) {
					newCoordinates.add(LocatePoint.pointAlongSegment(
							segment.p0, segment.p1, endDistance
									- segmentStartDistance), false);
				}
			}
			Coordinate[] newCoordinateArray = newCoordinates
					.toCoordinateArray();
			/**
			 * Ensure there is enough coordinates to build a valid line. Make a
			 * 2-point line with duplicate coordinates, if necessary There will
			 * always be at least one coordinate in the coordList.
			 */
			if (newCoordinateArray.length <= 1) {
				newCoordinateArray = new Coordinate[] { newCoordinateArray[0],
						newCoordinateArray[0] };
			}
			return line.getFactory().createLineString(newCoordinateArray);
		}
	}

}
