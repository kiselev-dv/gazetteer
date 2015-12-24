package me.osm.gazetteer.web.api;

import static me.osm.gazetteer.web.api.utils.RequestUtils.getSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import java.util.TreeSet;

import me.osm.gazetteer.web.ESNodeHolder;
import me.osm.gazetteer.web.api.meta.Endpoint;
import me.osm.gazetteer.web.api.meta.Parameter;
import me.osm.gazetteer.web.imp.IndexHolder;
import me.osm.gazetteer.web.utils.OSMDocSinglton;
import me.osm.osmdoc.model.Feature;
import me.osm.osmdoc.model.Tag;
import me.osm.osmdoc.read.OSMDocFacade;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Order;
import org.json.JSONArray;
import org.json.JSONObject;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.domain.metadata.UriMetadata;

public class StatisticAPI implements DocumentedApi {
	
	/**
	 * Features id's of higher objects to filter results.
	 * Array members will be added using OR
	 * */
	public static final String REFERENCES_HEADER = "filter";
	
	public JSONObject read(Request request, Response response) {
		
		String clazz = request.getHeader("poi_class");
		
		Set<String> refs = getSet(request, REFERENCES_HEADER);
		
		OSMDocFacade osmdoc = OSMDocSinglton.get().getFacade();
		Feature feature = osmdoc.getFeature(clazz);
		
		if(feature == null) {
			response.setResponseCode(404);
			return null;
		}

		Client client = ESNodeHolder.getClient();
		
		BoolQueryBuilder filters = QueryBuilders.boolQuery()
				.must(QueryBuilders.termQuery("type", "poipnt"))
				.must(QueryBuilders.termQuery("poi_class", clazz));
		
		if(refs != null && !refs.isEmpty()) {
			filters.must(QueryBuilders.termsQuery("refs", refs));
		}
		
		SearchRequestBuilder searchQ = client.prepareSearch("gazetteer").setTypes(IndexHolder.LOCATION)
				.setQuery(filters);
		
		LinkedHashMap<String, Tag> moreTags = osmdoc.getMoreTags(Arrays.asList(feature));

		for(Entry<String, Tag> entry : moreTags.entrySet()) {
			searchQ.addAggregation(AggregationBuilders.terms(entry.getKey()).field("more_tags." + entry.getKey()).minDocCount(10));
		}
		searchQ.addAggregation(AggregationBuilders.terms("name").field("name.exact")
				.minDocCount(10).size(25).order(Order.count(false)));
		
		searchQ.setSearchType(SearchType.COUNT);
		
		SearchResponse esResponse = searchQ.execute().actionGet();
		
		Aggregations aggregations = esResponse.getAggregations();
		
		JSONObject result = new JSONObject();
		result.put("poi_class", clazz);
		result.put("total_count", esResponse.getHits().getTotalHits());
		
		// Order tags by key
		JSONObject tags = new JSONObject() {
			@Override
			public Set<String> keySet() {
				return new TreeSet(super.keySet()); 
			}
		};
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

	@Override
	public Endpoint getMeta(UriMetadata uriMetadata) {
		Endpoint meta = new Endpoint(uriMetadata.getPattern(), "Tag values statistics", 
				"Tag values statistics for parsed tags.");
		
		meta.getPathParameters().add(new Parameter("poi-class", 
				"Poi class code."));
		
		return meta;
	}
	
}
