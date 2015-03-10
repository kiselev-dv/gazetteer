package me.osm.gazetteer.web.utils;

import me.osm.gazetteer.web.imp.Replacer;

public interface ReplacersFactory {
	public Replacer createReplacer(String pattern, String template);
}
