package me.osm.gazetteer.striper.builders.handlers;

import java.util.List;
import java.util.Map;

import me.osm.gazetteer.striper.readers.RelationsReader;
import me.osm.gazetteer.striper.readers.WaysReader;

import com.vividsolutions.jts.geom.LineString;

public interface HighwaysHandler extends FeatureHandler {

	public void handleHighway(LineString geometry, WaysReader.Way way);

	public void handleAssociatedStreet(int minN, int maxN, List<Long> wayId, List<RelationsReader.Relation.RelationMember> buildings, long relationId,
                                       Map<String, String> relAttributes);

}
