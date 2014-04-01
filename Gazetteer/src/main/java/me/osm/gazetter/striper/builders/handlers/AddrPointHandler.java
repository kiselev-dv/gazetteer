package me.osm.gazetter.striper.builders.handlers;

import java.util.Map;

import org.json.JSONObject;

import com.vividsolutions.jts.geom.Point;

public interface AddrPointHandler extends FeatureHandler {
	public void handleAddrPoint(Map<String, String> attributes, Point point, JSONObject meta);
	public void handleAddrPoint2Building(String n, long nodeId, long wayId, Map<String, String> wayTags);
}