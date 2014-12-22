package me.osm.gazetteer.web.api;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;


public class SuggestAPI extends SearchAPI {
	
	@Override
	public BoolQueryBuilder getSearchQuerry(Query querry) {
		
		
		BoolQueryBuilder searchQuerry = QueryBuilders.boolQuery();
		
		QueryBuilder q = QueryBuilders.prefixQuery("name", querry.tail().toString());
		Query head = querry.head();

		if(head == null) {
			searchQuerry.must(q)
			.mustNot(QueryBuilders.termQuery("weight", 0));
		}
		else {
			super.commonSearchQ(head, searchQuerry);
			searchQuerry.must(q);
		}
		
		
		return searchQuerry;
		
	}
}
