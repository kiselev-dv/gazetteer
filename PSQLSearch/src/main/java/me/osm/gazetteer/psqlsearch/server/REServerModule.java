package me.osm.gazetteer.psqlsearch.server;

import com.google.inject.Binder;
import com.google.inject.Module;

import me.osm.gazetteer.psqlsearch.api.SearchAPI;
import me.osm.gazetteer.psqlsearch.api.SearchAPIAdapter;
import me.osm.gazetteer.psqlsearch.api.search.DeafultSearch;
import me.osm.gazetteer.psqlsearch.api.search.Search;

public class REServerModule implements Module {

	@Override
	public void configure(Binder binder) {
		
		binder.bind(SearchAPI.class).to(SearchAPIAdapter.class);
		binder.bind(Search.class).to(DeafultSearch.class);
		
	}

}
