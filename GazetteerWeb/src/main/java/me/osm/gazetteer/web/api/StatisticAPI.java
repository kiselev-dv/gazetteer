package me.osm.gazetteer.web.api;

import static me.osm.gazetteer.web.api.utils.RequestUtils.getSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import me.osm.gazetteer.web.ESNodeHolder;
import me.osm.gazetteer.web.GazetteerWeb;
import me.osm.gazetteer.web.api.meta.Endpoint;
import me.osm.gazetteer.web.api.meta.Parameter;
import me.osm.gazetteer.web.api.utils.RequestUtils;
import me.osm.gazetteer.web.imp.IndexHolder;
import me.osm.gazetteer.web.utils.OSMDocSinglton;
import me.osm.osmdoc.localization.L10n;
import me.osm.osmdoc.model.Feature;
import me.osm.osmdoc.model.Tag;
import me.osm.osmdoc.read.OSMDocFacade;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.BoolQueryBuilder;
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
	
	private String apiDefaultHierarchy;
	
	public JSONObject read(Request request, Response response) {
		
		Set<String> classes = RequestUtils.getSet(request, SearchAPI.POI_CLASS_HEADER);
		
		Set<String> refs = getSet(request, REFERENCES_HEADER);
		
		Locale locale = null;
		if(L10n.supported.contains(request.getHeader("lang"))) {
			locale = Locale.forLanguageTag(request.getHeader("lang"));
		}
		
		apiDefaultHierarchy = GazetteerWeb.osmdocProperties().getApiDefaultHierarchy();
		String hname = request.getHeader(SearchAPI.HIERARCHY_CODE_HEADER, apiDefaultHierarchy);
		SearchAPI.addPOIGroups(request, classes, hname);
		
		boolean doc4Found = RequestUtils.getBooleanHeader(request, "doc4found", true);
		
		OSMDocFacade osmdoc = OSMDocSinglton.get().getFacade();
		
		List<Feature> features = new ArrayList<>();
		for(String clazz : classes) {
			Feature feature = osmdoc.getFeature(clazz);
			if(feature != null) {
				features.add(feature);
			}
		}
		
		if(features.isEmpty()) {
			response.setResponseCode(404);
			return null;
		}

		Client client = ESNodeHolder.getClient();
		
		BoolQueryBuilder filters = QueryBuilders.boolQuery()
				.must(QueryBuilders.termQuery("type", "poipnt"))
				.must(QueryBuilders.termsQuery("poi_class", classes));
		
		if(refs != null && !refs.isEmpty()) {
			filters.must(QueryBuilders.termsQuery("refs", refs));
		}
		
		SearchRequestBuilder searchQ = client.prepareSearch("gazetteer")
				.setTypes(IndexHolder.LOCATION)
				.setQuery(filters);
		
		JSONObject tagOptions = osmdoc.collectCommonTagsWithTraitsJSON(osmdoc.getFeature(classes), locale);
		Set<String> allTagKeys = getTagKeys(tagOptions);
		
		allTagKeys.removeAll(GazetteerWeb.osmdocProperties().getIgnoreTagsGrouping());

		for(String tagKey : allTagKeys) {
			searchQ.addAggregation(AggregationBuilders.terms(tagKey)
					.field("more_tags." + tagKey).minDocCount(10));
		}
		searchQ.addAggregation(AggregationBuilders.terms("name").field("name.exact")
				.minDocCount(10).size(25).order(Order.count(false)));
		
		searchQ.setSearchType(SearchType.COUNT);
		
		SearchResponse esResponse = searchQ.execute().actionGet();
		
		Aggregations aggregations = esResponse.getAggregations();
		
		JSONObject result = new JSONObject();
		result.put("poi_class", new JSONArray(classes));
		result.put("total_count", esResponse.getHits().getTotalHits());
		result.put("tag_options", tagOptions);
		
		// Order tags by key
		JSONObject statistic = new JSONObject();
		result.put("tagValuesStatistic", statistic);
		
		for(Aggregation agg : aggregations.asList()) {
			if(agg instanceof Terms) {
				Terms termsAgg = (Terms) agg;

				JSONObject values = new JSONObject();
				for(Bucket bucket : termsAgg.getBuckets()) {
					values.put(bucket.getKey(), bucket.getDocCount()); 
				}
				
				if("name".equals(agg.getName())) {
					result.put("names", values);
				}
				else if("type".equals(agg.getName())) {
					result.put("types", values);
				}
				else if(values.length() > 0) {
					statistic.put(agg.getName(), values);
				}
			}
		}
		
		if(doc4Found) {
			Set<String> foundedKeys = statistic.keySet();
			Set<String> notFound = new HashSet<>(allTagKeys);
			notFound.removeAll(foundedKeys);
			
			JSONObject groupedTags = tagOptions.getJSONObject("groupedTags");
			JSONArray options = tagOptions.getJSONArray("commonTagOptions");
			
			for(String notFoundKey : notFound) {
				groupedTags.remove(notFoundKey);
			}

			TreeSet<Integer> remove = new TreeSet<>();
			for(int i = 0; i < options.length(); i++) {
				JSONObject filter = options.getJSONObject(i);
				if(notFound.contains(filter.getString("key"))) {
					remove.add(i);
				}
				else if(filter.getString("key").startsWith("trait_")) {
					JSONArray group = filter.optJSONArray("options");
					
					TreeSet<Integer> gropRemove = new TreeSet<>();
					for(int j = 0; j < group.length(); j++) {
						if(notFound.contains(group.getJSONObject(j).getString("valueKey"))) {
							gropRemove.add(j);
						}
					}
					
					for(Iterator<Integer> gri = gropRemove.descendingIterator(); gri.hasNext();) {
						group.remove(gri.next());
					}
					
					if(group.length() == 0) {
						remove.add(i);
					}
				}
			}
			
			for(Iterator<Integer> ri = remove.descendingIterator(); ri.hasNext();) {
				options.remove(ri.next());
			}
			
		}
		
		return result;
	}

	private Set<String> getTagKeys(JSONObject tagOptions) {

		Set<String> result = new HashSet<>();
		
		JSONArray tagOptionsJSON = tagOptions.optJSONArray("commonTagOptions");
		for(int i = 0; i < tagOptionsJSON.length(); i++) {
			JSONObject jsonObject = tagOptionsJSON.getJSONObject(i);
			if(!jsonObject.getString("type").equals("GROUP_TRAIT")) {
				result.add(jsonObject.getString("key"));
			}
		}
		
		result.addAll(tagOptions.getJSONObject("groupedTags").keySet());
		
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
