package me.osm.gazetteer.web.api;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

import me.osm.gazetteer.web.ESNodeHodel;
import me.osm.gazetteer.web.imp.IndexHolder;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.json.JSONArray;
import org.json.JSONObject;
import org.restexpress.Request;
import org.restexpress.Response;


public class SuggestAPI extends SearchAPI {
	
	@Override
	public JSONObject read(Request request, Response response)
			throws IOException {
		
		String querryString = StringUtils.stripToNull(request.getHeader(Q_HEADER));
		Query query = queryAnalyzer.getQuery(querryString);
		JSONObject type = suggestPoiType(query);

		if(type == null) {
			return super.read(request, response);
		} 
		
		JSONObject result = new JSONObject();
		result.put("result", "success");
		
		JSONArray features = new JSONArray();
		result.put("features", features);
		
		result.put("matched_type", type);
		
		result.put("hits", 1);
		
		return result;
	}
	
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

	private JSONObject suggestPoiType(Query query) {
		Client client = ESNodeHodel.getClient();
		
		String qs = query.filter(new HashSet<String>(Arrays.asList("на", "дом"))).toString();
		
		SearchRequestBuilder searchRequest = client.prepareSearch("gazetteer").setTypes(IndexHolder.POI_CLASS)
				.setQuery(QueryBuilders.prefixQuery("translated_title", qs));
		
		SearchHit[] hits = searchRequest.get().getHits().getHits();

		if(hits.length > 0) {
			return new JSONObject(hits[0].sourceAsString());
		}
		
		return null;
	}
	
	
	
}
