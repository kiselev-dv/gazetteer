package me.osm.gazetteer.psqlsearch.sqlquery;

public final class StandardSearchQueryRow {
	double rank;
	String fullText;
	String json;
	String osmId;
	
	public double getRank() {
		return rank;
	}
	
	public String getFullText() {
		return fullText;
	}
	
	public String getJson() {
		return json;
	}

	public String getOsmId() {
		return osmId;
	}
	
}