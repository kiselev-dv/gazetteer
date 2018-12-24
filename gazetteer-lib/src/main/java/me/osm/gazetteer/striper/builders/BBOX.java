package me.osm.gazetteer.striper.builders;

public class BBOX {
	double minX = Double.NaN;
	double minY = Double.NaN;

	double maxX = Double.NaN;
	double maxY = Double.NaN;

	public void extend(double x, double y) {
		if(Double.isNaN(minX)) {
			minX = x;
			maxX = x;
			minY = y;
			maxY = y;
		}
		else {
			minX = Math.min(minX, x);
			maxX = Math.max(maxX, x);

			minY = Math.min(minY, y);
			maxY = Math.max(maxY, y);
		}
	}

	public double getDX() {
		return maxX - minX;
	}
}
