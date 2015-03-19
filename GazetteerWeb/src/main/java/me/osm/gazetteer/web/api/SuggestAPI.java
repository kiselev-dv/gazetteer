package me.osm.gazetteer.web.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import me.osm.gazetteer.web.ESNodeHodel;
import me.osm.gazetteer.web.api.imp.Query;
import me.osm.gazetteer.web.imp.IndexHolder;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.DisMaxQueryBuilder;
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
		List<JSONObject> type = suggestPoiType(query);

		JSONObject answer = super.read(request, response);
		
		answer.put("matched_type", new JSONArray(type));
		
		return answer;
	}
	
	@Override
	public BoolQueryBuilder getSearchQuerry(Query querry, boolean strict) {
		
		
		BoolQueryBuilder searchQuerry = QueryBuilders.boolQuery();
		
		QueryBuilder prefQ = null;
		
		Query tail = querry.tail();
		if(tail.countNumeric() == 1) {
			prefQ = QueryBuilders.matchQuery("search", tail.toString());
		}
		else {
			prefQ = QueryBuilders.prefixQuery("name.text", tail.toString());
		}
		
		Query head = querry.head();

		if(head == null) {
			searchQuerry.must(prefQ)
			.mustNot(QueryBuilders.termQuery("weight", 0));
		}
		else {
			super.commonSearchQ(head, searchQuerry, strict);
			searchQuerry.must(prefQ);
		}
		
		return searchQuerry;
		
	}

	private List<JSONObject> suggestPoiType(Query query) {
		Client client = ESNodeHodel.getClient();
		
		Query filtered = query.filter(new HashSet<String>(Arrays.asList("на", "дом")));
		
		DisMaxQueryBuilder dismax = QueryBuilders.disMaxQuery()
				.add(QueryBuilders.prefixQuery("translated_title", filtered.toString()));
		
		dismax.add(QueryBuilders.prefixQuery("translated_title", query.tail().toString()));
		if(!query.listToken().isEmpty()) {
			dismax.add(QueryBuilders.prefixQuery("translated_title", query.listToken().get(0).toString()));
		}
		
		SearchRequestBuilder searchRequest = client.prepareSearch("gazetteer")
				.setTypes(IndexHolder.POI_CLASS)
				.setQuery(dismax);
		
		SearchHit[] hits = searchRequest.get().getHits().getHits();

		List<JSONObject> types = new ArrayList<JSONObject>(hits.length);
		if(hits.length > 0) {
			for(SearchHit hit : hits) {
				types.add(new JSONObject(hit.getSourceAsString()));
			}
		}
		
		return types;
	}
	
	
	
}
