package me.osm.gazetter.striper.builders;

import java.util.Map;

import org.json.JSONObject;

import com.vividsolutions.jts.geom.Point;

import me.osm.gazetter.striper.readers.PointsReader.Node;
import me.osm.gazetter.striper.readers.RelationsReader.Relation;
import me.osm.gazetter.striper.readers.WaysReader.Way;

public class PoisBuilder extends ABuilder {

	public static interface PoisHandler extends FeatureHandler {
		public void handlePoi(Map<String, String> attributes, Point point, JSONObject meta); 
	}

	private PoisHandler handler;
	
	public PoisBuilder(PoisHandler handler) {
		this.handler = handler;
	}
	
	@Override
	public void firstRunDoneRelations() {
		handler.freeThreadPool(getThreadPoolUser());
	}

	@Override
	public void handle(Relation rel) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void handle(Way line) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void handle(Node node) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void secondRunDoneRelations() {
		handler.freeThreadPool(getThreadPoolUser());
	}


}
