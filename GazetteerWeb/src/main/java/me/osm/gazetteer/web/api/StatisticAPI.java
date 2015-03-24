package me.osm.gazetteer.web.api;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import me.osm.gazetteer.web.ESNodeHodel;
import me.osm.gazetteer.web.imp.IndexHolder;
import me.osm.gazetteer.web.utils.OSMDocSinglton;
import me.osm.osmdoc.model.Feature;
import me.osm.osmdoc.model.Tag;
import me.osm.osmdoc.read.OSMDocFacade;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.json.JSONObject;
import org.restexpress.Request;
import org.restexpress.Response;

public class StatisticAPI {
	
	public JSONObject read(Request request, Response response) {
		
		String clazz = request.getHeader("poi_class");
		
		
		OSMDocFacade osmdoc = OSMDocSinglton.get().getFacade();
		Feature feature = osmdoc.getFeature(clazz);
		
		if(feature == null) {
			response.setResponseCode(404);
			return null;
		}

		Client client = ESNodeHodel.getClient();
		
		SearchRequestBuilder searchQ = client.prepareSearch("gazetteer").setTypes(IndexHolder.LOCATION)
				.setQuery(QueryBuilders.boolQuery()
						.must(QueryBuilders.termQuery("type", "poipnt"))
						.must(QueryBuilders.termQuery("poi_class", clazz)));
		
		LinkedHashMap<String, Tag> moreTags = osmdoc.getMoreTags(Arrays.asList(feature));
		
		for(Entry<String, Tag> entry : moreTags.entrySet()) {
			searchQ.addAggregation(AggregationBuilders.terms(entry.getKey()).field("more_tags." + entry.getKey()).minDocCount(10));
		}
		
		searchQ.setSearchType(SearchType.COUNT);
		
		SearchResponse esResponse = searchQ.execute().actionGet();
		
		Aggregations aggregations = esResponse.getAggregations();
		
		JSONObject result = new JSONObject();
		result.put("poi_class", clazz);
		
		JSONObject tags = new JSONObject();
		result.put("tags", tags);
		for(Aggregation agg : aggregations.asList()) {
			if(agg instanceof Terms) {
				Terms termsAgg = (Terms) agg;

				JSONObject values = new JSONObject();
				for(Bucket bucket : termsAgg.getBuckets()) {
					values.put(bucket.getKey(), bucket.getDocCount()); 
				}
				
				tags.put(agg.getName(), values);
			}
		}
		
		return result;
	}
	
}
