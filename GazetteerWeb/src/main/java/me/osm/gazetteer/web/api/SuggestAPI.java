package me.osm.gazetteer.web.api;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;


public class SuggestAPI extends SearchAPI {
	
	@Override
	public BoolQueryBuilder getSearchQuerry(Query querry) {
		
		
		BoolQueryBuilder searchQuerry = QueryBuilders.boolQuery();
		
		QueryBuilder prefQ = null;
		
		Query tail = querry.tail();
		if(tail.countNumeric() == 1) {
			prefQ = QueryBuilders.matchQuery("search", tail.toString());
		}
		else {
			prefQ = QueryBuilders.prefixQuery("name", tail.toString());
		}
		
		Query head = querry.head();

		if(head == null) {
			searchQuerry.must(prefQ)
			.mustNot(QueryBuilders.termQuery("weight", 0));
		}
		else {
			super.commonSearchQ(head, searchQuerry);
			searchQuerry.must(prefQ);
		}
		
		return searchQuerry;
		
	}
}
