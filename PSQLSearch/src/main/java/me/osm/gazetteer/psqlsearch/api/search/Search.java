package me.osm.gazetteer.psqlsearch.api.search;

import me.osm.gazetteer.psqlsearch.api.ResultsWrapper;

public interface Search {

	ResultsWrapper search(String query, boolean prefix, Double lon, Double lat, boolean addressesOnly, int page, int pageSize);
	
	ResultsWrapper search(String query, double[] bbox, String[] poiTypes, int page, int pageSize);

}
