package me.osm.gazetter.test;

import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import me.osm.gazetter.striper.builders.BuildUtils;
import me.osm.gazetter.striper.readers.RelationsReader.Relation;

import org.junit.Test;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;

/**
 * See http://wiki.openstreetmap.org/wiki/Multipolygon
 * */
public class MultiPolygonBuilderTest {
	
	private static final GeometryFactory f = new GeometryFactory();
	
	@Test
	public void oneOuterRing() {
		
		List<LineString> outers = new ArrayList<LineString>();
		
		outers.add(f.createLineString(new Coordinate[]{
				new Coordinate(1, 1),
				new Coordinate(1, 2),
				new Coordinate(2, 2),
				new Coordinate(2, 1),
				new Coordinate(1, 1)
		}));
		
		MultiPolygon mp = BuildUtils.buildMultyPolygon(new Relation(), outers, null);
		
		assertNotNull(mp);
		System.out.println("oneOuterRing: " + mp.toString());
	}

	@Test
	public void oneOuterAndOneInnerRing() {
		
		List<LineString> outers = new ArrayList<LineString>();
		outers.add(f.createLineString(new Coordinate[]{
				new Coordinate(0, 0),
				new Coordinate(10, 0),
				new Coordinate(10, 10),
				new Coordinate(0, 10),
				new Coordinate(0, 0)
		}));

		List<LineString> inners = new ArrayList<LineString>();
		inners.add(f.createLineString(new Coordinate[]{
				new Coordinate(2, 2),
				new Coordinate(8, 2),
				new Coordinate(8, 8),
				new Coordinate(2, 8),
				new Coordinate(2, 2)
		}));
		
		MultiPolygon mp = BuildUtils.buildMultyPolygon(new Relation(), outers, inners);
		
		assertNotNull(mp);
		System.out.println("oneOuterAndOneInnerRing: " + mp.toString());
	}
	
	@Test
	public void multipleWaysFormingRing() {
		
		List<LineString> outers = new ArrayList<LineString>();
		
		outers.add(f.createLineString(new Coordinate[]{
				new Coordinate(1, 1),
				new Coordinate(1, 2),
				new Coordinate(2, 2)
		}));

		outers.add(f.createLineString(new Coordinate[]{
				new Coordinate(2, 2),
				new Coordinate(2, 1),
				new Coordinate(1, 1)
		}));
		
		MultiPolygon mp = BuildUtils.buildMultyPolygon(new Relation(), outers, null);
		
		assertNotNull(mp);
		System.out.println("multipleWaysFormingRing: " + mp.toString());
	}

	@Test
	public void twoDisjunctOuterRings() {
		
		List<LineString> outers = new ArrayList<LineString>();
		
		outers.add(f.createLineString(new Coordinate[]{
				new Coordinate(0, 0),
				new Coordinate(10, 0),
				new Coordinate(10, 10),
				new Coordinate(0, 10),
				new Coordinate(0, 0)
		}));

		outers.add(f.createLineString(new Coordinate[]{
				new Coordinate(20, 0),
				new Coordinate(30, 0),
				new Coordinate(30, 10),
				new Coordinate(20, 10),
				new Coordinate(20, 0)
		}));
		
		MultiPolygon mp = BuildUtils.buildMultyPolygon(new Relation(), outers, null);
		
		assertNotNull(mp);
		System.out.println("twoDisjunctOuterRings: " + mp.toString());
	}
	
	@Test
	public void twoDisjunctOuterRingsAndMultipleWaysFormingRing() {
		
		List<LineString> outers = new ArrayList<LineString>();
		List<LineString> inners = new ArrayList<LineString>();
		
		outers.add(f.createLineString(new Coordinate[]{
				new Coordinate(0, 0),
				new Coordinate(10, 0),
				new Coordinate(10, 10),
				new Coordinate(0, 10),
				new Coordinate(0, 0)
		}));
		
		inners.add(f.createLineString(new Coordinate[]{
				new Coordinate(2, 2),
				new Coordinate(8, 2),
				new Coordinate(8, 8)
		}));

		inners.add(f.createLineString(new Coordinate[]{
				new Coordinate(8, 8),
				new Coordinate(2, 8),
				new Coordinate(2, 2)
		}));

		outers.add(f.createLineString(new Coordinate[]{
				new Coordinate(20, 0),
				new Coordinate(30, 0),
				new Coordinate(30, 10),
				new Coordinate(20, 10),
				new Coordinate(20, 0)
		}));
		
		inners.add(f.createLineString(new Coordinate[]{
				new Coordinate(22, 2),
				new Coordinate(28, 2),
				new Coordinate(28, 8),
				new Coordinate(22, 8),
				new Coordinate(22, 2)
		}));
		
		MultiPolygon mp = BuildUtils.buildMultyPolygon(new Relation(), outers, inners);
		
		assertNotNull(mp);
		System.out.println("twoDisjunctOuterRingsAndMultipleWaysFormingRing: " + mp.toString());
	}

	@Test
	public void touchingInnerRings() {
		
		List<LineString> outers = new ArrayList<LineString>();
		List<LineString> inners = new ArrayList<LineString>();
		
		outers.add(f.createLineString(new Coordinate[]{
				new Coordinate(0, 0),
				new Coordinate(10, 0),
				new Coordinate(10, 10),
				new Coordinate(0, 10),
				new Coordinate(0, 0)
		}));
		
		inners.add(f.createLineString(new Coordinate[]{
				new Coordinate(2, 2),
				new Coordinate(8, 2),
				new Coordinate(8, 5),
				new Coordinate(2, 5),
				new Coordinate(2, 2)
		}));

		inners.add(f.createLineString(new Coordinate[]{
				new Coordinate(2, 5),
				new Coordinate(8, 5),
				new Coordinate(8, 8),
				new Coordinate(2, 8),
				new Coordinate(2, 5)
		}));
		
		MultiPolygon mp = BuildUtils.buildMultyPolygon(new Relation(), outers, inners);
		
		assertNotNull(mp);
		System.out.println("touchingInnerRings: " + mp.toString());
	}
	
	@Test
	public void islandWithinHole() {
		
		List<LineString> outers = new ArrayList<LineString>();
		List<LineString> inners = new ArrayList<LineString>();
		
		outers.add(f.createLineString(new Coordinate[]{
				new Coordinate(0, 0),
				new Coordinate(10, 0),
				new Coordinate(10, 10),
				new Coordinate(0, 10),
				new Coordinate(0, 0)
		}));
		
		inners.add(f.createLineString(new Coordinate[]{
				new Coordinate(2, 2),
				new Coordinate(8, 2),
				new Coordinate(8, 8),
				new Coordinate(2, 8),
				new Coordinate(2, 2)
		}));
		
		outers.add(f.createLineString(new Coordinate[]{
				new Coordinate(4, 4),
				new Coordinate(6, 4),
				new Coordinate(6, 6),
				new Coordinate(4, 6),
				new Coordinate(4, 4)
		}));
		
		MultiPolygon mp = BuildUtils.buildMultyPolygon(new Relation(), outers, inners);
		
		assertNotNull(mp);
		System.out.println("islandWithinHole: " + mp.toString());
	}

	@Test
	public void islandWithinHoleWithDisjuncOuter() {
		
		List<LineString> outers = new ArrayList<LineString>();
		List<LineString> inners = new ArrayList<LineString>();
		
		outers.add(f.createLineString(new Coordinate[]{
				new Coordinate(0, 0),
				new Coordinate(10, 0),
				new Coordinate(10, 10),
				new Coordinate(0, 10),
				new Coordinate(0, 0)
		}));
		
		inners.add(f.createLineString(new Coordinate[]{
				new Coordinate(2, 2),
				new Coordinate(8, 2),
				new Coordinate(8, 8),
				new Coordinate(2, 8),
				new Coordinate(2, 2)
		}));
		
		outers.add(f.createLineString(new Coordinate[]{
				new Coordinate(4, 4),
				new Coordinate(6, 4),
				new Coordinate(6, 6),
				new Coordinate(4, 6),
				new Coordinate(4, 4)
		}));
		
		outers.add(f.createLineString(new Coordinate[]{
				new Coordinate(20, 0),
				new Coordinate(30, 0),
				new Coordinate(30, 10),
				new Coordinate(20, 10),
				new Coordinate(20, 0)
		}));
		
		MultiPolygon mp = BuildUtils.buildMultyPolygon(new Relation(), outers, inners);
		
		assertNotNull(mp);
		System.out.println("islandWithinHoleWithDisjuncOuter: " + mp.toString());
	}
	
	@Test
	public void realWorldExample1() throws ParseException {
		
		List<LineString> outers = new ArrayList<LineString>();
		List<LineString> inners = new ArrayList<LineString>();
		
		WKTReader reader = new WKTReader(f);
		outers.add((LineString) reader.read("LINESTRING (42.8211884 45.3375219, 42.821406 45.3375338, "
				+ "	42.821478 45.3375392, 42.8214348 45.3379158, 42.8214215 45.3379422)"));
		//outers.add((LineString) reader.read("LINESTRING (42.821046 45.3378754, 42.8208881 45.3378474)"));
		outers.add((LineString) reader.read("LINESTRING (42.8214215 45.3379422, 42.821046 45.3378754, "
				+ "42.8208881 45.3378474, 42.8206616 45.3378149, 42.8205843 45.3378055, 42.8206528 45.3374546, "
				+ "42.8208394 45.3374756, 42.8211884 45.3375219)"));
		
		MultiPolygon mp = BuildUtils.buildMultyPolygon(new Relation(), outers, inners);
		
		assertNotNull(mp);
		System.out.println("realWorldExample1: " + mp.toString());
	}
	
	
	
}
