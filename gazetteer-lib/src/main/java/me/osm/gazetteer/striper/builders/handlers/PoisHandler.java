package me.osm.gazetteer.striper.builders.handlers;

import java.util.Map;
import java.util.Set;

import org.json.JSONObject;

import com.vividsolutions.jts.geom.Point;

public interface PoisHandler extends FeatureHandler {
	public void handlePoi(Set<String> types, Map<String, String> attributes, Point point, JSONObject meta);
	public void handlePoi2Building(String n, long nodeId, long lineId, Map<String, String> linetags);
}
