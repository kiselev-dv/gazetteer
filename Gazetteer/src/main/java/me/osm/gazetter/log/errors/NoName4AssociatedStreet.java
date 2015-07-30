package me.osm.gazetter.log.errors;

import org.json.JSONObject;
import org.slf4j.Logger;

import me.osm.gazetter.log.GazetteerLogMessage;
import me.osm.gazetter.log.LogLevel;
import me.osm.gazetter.log.LogLevel.Level;

/**
 * Street was matched, but, street way, 
 * neighter relation have not name=* tag.
 * */
public class NoName4AssociatedStreet extends GazetteerLogMessage {

	private static final long serialVersionUID = 7197731549390098951L;

	private transient JSONObject street;
	private transient JSONObject relation;

	@SuppressWarnings("unused")
	private String streetId;
	
	@SuppressWarnings("unused")
	private String relationId;
	
	public NoName4AssociatedStreet(JSONObject street, JSONObject relation) {
		this.street = street;
		this.relation = relation;
		
		this.streetId = street.optString("id"); 
		this.relationId = relation.optString("id"); 
	}

	@Override
	public void log(Logger root, Level level) {
		LogLevel.log(root, level, 
				"Can't find name for associated street.\nStreet:\n{}\nRelation\n{}", street, relation);
	}

}
