package me.osm.gazetteer.psqlsearch.query;


public interface QueryAnalyzer {

	public Query getQuery(String q);

}