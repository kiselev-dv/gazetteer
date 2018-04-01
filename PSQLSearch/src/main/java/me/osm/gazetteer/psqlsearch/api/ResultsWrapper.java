package me.osm.gazetteer.psqlsearch.api;

import java.util.ArrayList;
import java.util.List;

public class ResultsWrapper {
	
	private int page;
	private int pageSize;
	private String query;
	
	private List<SearchResultRow> rows = new ArrayList<>();
	
	public static final class SearchResultRow {
		private double rank;
		private String full_text;
		private String osm_id;
	}
	
	private String error;
	
	public void addResultsRow(double rank, String fullText, String osmId) {
		SearchResultRow row = new SearchResultRow();
		
		row.rank = rank;
		row.full_text = fullText;
		row.osm_id = osmId;
				
		rows.add(row);
	}

	public void setPage(int page) {
		this.page = page;
	}
	public void setPageSize(int pageSize) {
		this.pageSize = pageSize;
	}
	public void setQuery(String query) {
		this.query = query;
	}
	public void setErrorMessage(String message) {
		this.error = message;
	}
	
}
