package me.osm.gazetteer.psqlsearch.server.postprocessor;

import java.util.Date;

public interface Timestamped {
	
	public Date getUpdatedAt();
	
}
