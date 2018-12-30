package me.osm.gazetteer.striper.builders.handlers;

import java.util.Map;

import org.json.JSONObject;

import com.vividsolutions.jts.geom.Point;

public interface PlacePointHandler extends FeatureHandler {
	public void handlePlacePoint(Map<String, String> tags, Point pnt,
			JSONObject meta);

	public void writeOut(String line, String n);
}
