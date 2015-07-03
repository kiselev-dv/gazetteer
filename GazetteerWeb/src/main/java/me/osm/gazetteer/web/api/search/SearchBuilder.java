package me.osm.gazetteer.web.api.search;

import me.osm.gazetteer.web.api.query.Query;
import me.osm.gazetteer.web.api.utils.BuildSearchQContext;

import org.elasticsearch.index.query.BoolQueryBuilder;

public interface SearchBuilder {
	
	/**
	 * Creates main search query.
	 * 
	 * @param query analyzed user query
	 * @param resultQuery parent query
	 * @param strict create strict version of query
	 * */
	public void mainSearchQ(Query query, BoolQueryBuilder resultQuery,
			boolean strict, BuildSearchQContext context);

	
}
