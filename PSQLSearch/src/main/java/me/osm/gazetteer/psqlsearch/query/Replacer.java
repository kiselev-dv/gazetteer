package me.osm.gazetteer.psqlsearch.query;

import java.util.Collection;
import java.util.Map;

public interface Replacer {
	
	public Collection<String> replace(String hn);

	public Map<String, Collection<String>> replaceGroups(String hn);

}
