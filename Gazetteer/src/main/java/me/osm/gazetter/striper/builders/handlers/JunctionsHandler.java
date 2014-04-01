package me.osm.gazetter.striper.builders.handlers;

import java.util.List;

import com.vividsolutions.jts.geom.Coordinate;

public interface JunctionsHandler extends FeatureHandler {
	public void handleJunction(Coordinate coordinates, long nodeID,
			List<Long> highways);
}