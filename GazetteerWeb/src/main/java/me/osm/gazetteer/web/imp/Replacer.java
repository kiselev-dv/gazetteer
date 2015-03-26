package me.osm.gazetteer.web.imp;

import java.util.Collection;
import java.util.Map;

public interface Replacer {
	
	public Collection<String> replace(String hn);

	public Map<String, Collection<String>> replaceGroups(String hn);

}
