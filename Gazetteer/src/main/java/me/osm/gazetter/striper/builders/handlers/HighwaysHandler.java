package me.osm.gazetter.striper.builders.handlers;

import java.util.List;
import java.util.Map;

import me.osm.gazetter.striper.readers.RelationsReader.Relation.RelationMember;
import me.osm.gazetter.striper.readers.WaysReader.Way;

import com.vividsolutions.jts.geom.LineString;

public interface HighwaysHandler extends FeatureHandler {
	
	public void handleHighway(LineString geometry, Way way);
	
	public void handleAssociatedStreet(int minN, int maxN, List<Long> wayId, List<RelationMember> buildings, long relationId, 
			Map<String, String> relAttributes);
	
}