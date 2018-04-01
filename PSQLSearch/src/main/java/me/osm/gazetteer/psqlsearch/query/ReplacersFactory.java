package me.osm.gazetteer.psqlsearch.query;

public interface ReplacersFactory {
	public Replacer createReplacer(String pattern, String template);
}
