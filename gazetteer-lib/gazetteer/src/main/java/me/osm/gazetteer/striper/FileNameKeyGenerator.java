package me.osm.gazetteer.striper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

public class FileNameKeyGenerator {

	public static double round(double value, int places) {
	    if (places < 0) throw new IllegalArgumentException();

	    BigDecimal bd = new BigDecimal(value);
	    bd = bd.setScale(places, RoundingMode.HALF_UP);
	    return bd.doubleValue();
	}

	private int fx = 1;
	private double dx = 0.1 / fx;
	private double dxinv = 1/dx;
	private double x0 = 0;
	private int charsX = 4 + fx / 10;
	private int roundPlacesX = 4 + fx / 10;

	private int fy = 1;
	private double dy = 180.0 / fy;
	private double dyinv = 1/dx;
	private double y0 = 0;
	private int charsY = 4 + fy / 10;
	private int roundPlacesY = 2 + fy / 10;

	private String FILE_MASK_X = "%0" + charsX + "d";
	private String FILE_MASK_Y = "";
	private GeometryFactory factory = new GeometryFactory();


	public FileNameKeyGenerator(int xfactor, int yfactor) {
		this.fx = xfactor;
		this.fy = yfactor;

		dx = 0.1 / fx;
		dxinv = 1/dx;
		x0 = 0;

		charsX = 4 + fx / 10;
		roundPlacesX = 4 + fx / 10;
		FILE_MASK_X = "%0" + charsX + "d";

		dy = 180.0 / fy;
		dyinv = 1/dx;
		y0 = 0;
		charsY = 4 + fy / 10;
		roundPlacesY = 2 + fy / 10;

	}

	public double snapX(double x) {
		return Math.round((x - x0)/ dx) * dx + x0;
	}

	public double roundX(Point centroid) {
		return round(snapX(centroid.getX()), roundPlacesX);
	}

	public String getFilePrefix(double x, double y) {
		String xpref = String.format(FILE_MASK_X, (new Double((x + 180.0) * dxinv).intValue()));
		String ypref = String.format(FILE_MASK_Y, (new Double((y + 90.0) * dyinv).intValue()));

		return xpref + ypref;
	}

	public List<Polygon> getBladePolygons(Envelope bbox) {

		double minX = bbox.getMinX();
		double maxX = bbox.getMaxX();
		List<Double> bladesX = getBlades(bbox);

		List<Polygon> polygons = new ArrayList<>();
		double x1 = minX;
		double x2 = maxX;
		for(double x : bladesX) {
			x2 = x;
			polygons.add(
				factory.createPolygon(new Coordinate[]{
					new Coordinate(x1, bbox.getMinY() - 0.00001),
					new Coordinate(x2, bbox.getMinY() - 0.00001),
					new Coordinate(x2, bbox.getMaxY() + 0.00001),
					new Coordinate(x1, bbox.getMaxY() + 0.00001),
					new Coordinate(x1, bbox.getMinY() - 0.00001)
			}));
			x1 = x2;
		}
		x2 = maxX;
		polygons.add(
			factory.createPolygon(new Coordinate[]{
					new Coordinate(x1, bbox.getMinY() - 0.00001),
					new Coordinate(x2, bbox.getMinY() - 0.00001),
					new Coordinate(x2, bbox.getMaxY() + 0.00001),
					new Coordinate(x1, bbox.getMaxY() + 0.00001),
					new Coordinate(x1, bbox.getMinY() - 0.00001)
		}));

		return polygons;
	}

	public List<Double> getBlades(Envelope bbox) {
		double minX = bbox.getMinX();
		double maxX = bbox.getMaxX();

		List<Double> bladesX = new ArrayList<>();
		for(double x = minX;x <= maxX;x += dx) {
			double snapX = round(snapX(x), roundPlacesX);

			if(snapX > minX && snapX < maxX) {
				bladesX.add(snapX);
			}
		}
		return bladesX;
	}

	public int getMinX(Envelope env) {
		return new Double((env.getMinX() + 180.0) * dxinv).intValue();
	}

	public int getMaxX(Envelope env) {
		return new Double((env.getMaxX() + 180.0) * dxinv).intValue();
	}

	public String format(int x, int y) {
		return String.format(FILE_MASK_X, x);
	}
}
