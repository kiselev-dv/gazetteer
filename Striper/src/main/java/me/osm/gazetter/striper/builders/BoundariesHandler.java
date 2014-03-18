package me.osm.gazetter.striper.builders;

import org.json.JSONObject;

import com.vividsolutions.jts.geom.MultiPolygon;

public interface BoundariesHandler {
	public void handleBoundary(JSONObject feature, MultiPolygon multiPolygon);
	void beforeLastRun();
	void afterLastRun();
}