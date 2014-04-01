package me.osm.gazetter.striper.builders.handlers;

import org.json.JSONObject;

import com.vividsolutions.jts.geom.MultiPolygon;

public interface BoundariesHandler extends FeatureHandler {
	public void handleBoundary(JSONObject feature, MultiPolygon multiPolygon);
}