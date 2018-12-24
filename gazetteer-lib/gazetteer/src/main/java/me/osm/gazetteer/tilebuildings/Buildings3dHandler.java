package me.osm.gazetteer.tilebuildings;

import java.util.List;
import java.util.Map;

import com.vividsolutions.jts.geom.Point;

import me.osm.gazetteer.striper.readers.WaysReader;
import me.osm.gazetteer.striper.readers.PointsReader.Node;
import me.osm.gazetteer.striper.readers.RelationsReader.Relation;

public interface Buildings3dHandler {

	// Keep in memory only the tagged nodes,
	// like entrances
	void saveNode(Node node);


	void handleWay(WaysReader.Way line, List<Point> coords);

	// Save it in memory, it might be needed to save it into
	// two separate tiles to keep tiles consistent
	void saveRelationWay(WaysReader.Way line, List<Point> coords);

	void handleRelation(Relation rel, Map<Long, List<Point>> relationWays, List<Point> relationPoints);

	void close();

}
