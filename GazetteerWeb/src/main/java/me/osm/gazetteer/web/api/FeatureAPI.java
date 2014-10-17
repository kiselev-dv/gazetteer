package me.osm.gazetteer.web.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import me.osm.gazetteer.web.ESNodeHodel;
import me.osm.gazetteer.web.FeatureTypes;
import me.osm.gazetteer.web.imp.Importer;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.FilteredQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.highlight.HighlightBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.restexpress.Request;
import org.restexpress.Response;

import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonArrayFormatVisitor;

public class FeatureAPI {
	
	private static Map<String, Set<String>> typesMap = new HashMap<String, Set<String>>();
	static {
		
		typesMap.put("adr", new HashSet<String>(Arrays.asList(new String[]{
				FeatureTypes.POI_FTYPE, FeatureTypes.HIGHWAY_FEATURE_TYPE	
		})));

		typesMap.put("poi", new HashSet<String>(Arrays.asList(new String[]{
				FeatureTypes.ADDR_POINT_FTYPE, FeatureTypes.HIGHWAY_FEATURE_TYPE	
		})));

		typesMap.put("highway", new HashSet<String>(Arrays.asList(new String[]{
				FeatureTypes.ADDR_POINT_FTYPE, FeatureTypes.POI_FTYPE	
		})));

		typesMap.put("place", new HashSet<String>(Arrays.asList(new String[]{
				FeatureTypes.HIGHWAY_FEATURE_TYPE	
		})));

		typesMap.put("region", new HashSet<String>(Arrays.asList(new String[]{
				FeatureTypes.PLACE_BOUNDARY_FTYPE, FeatureTypes.PLACE_POINT_FTYPE 	
		})));

		typesMap.put("country", new HashSet<String>(Arrays.asList(new String[]{
				FeatureTypes.ADMIN_BOUNDARY_FTYPE	
		})));
	}

	private static final String[] COMMON_FIELDS = new String[]{
		"feature_id",
		"type",
		"timestamp",
		"name",
		"alt_names",
		"nearby_streets",
		"nearby_places",
		"nearest_place",
		"nearest_neighbour",
		"tags",
        "poi_class",
        "poi_class_names",
        "operator",
        "brand",
        "opening_hours",
        "phone",
        "email",
        "website",
        "center_point"
	};

	private static final String[] ADDR_ROW_FIELDS = new String[] {
		"id",
		"address",
		"alt_addresses",
		"scheme",
		"admin0_name",
		"admin0_alternate_names",
		"admin1_name",
		"admin1_alternate_names",
		"admin2_name",
		"admin2_alternate_names",
		"local_admin_name",
		"local_admin_alternate_names",
		"locality_name",
		"locality_alternate_names",
		"neighborhood_name",
		"neighborhood_alternate_names",
		"street_name",
		"street_alternate_names",
		"housenumber",
		"refs"
	};

	public JSONObject read(Request request, Response response) 
			throws IOException {
		
		String idParam = request.getHeader("id");
		String row = request.getHeader("row");
		if(StringUtils.isEmpty(idParam) && StringUtils.isNotEmpty(row)) {
			String[] parts = StringUtils.split(row, '-');
			if(parts[0].equals(FeatureTypes.ADDR_POINT_FTYPE) || 
					parts[0].equals(FeatureTypes.POI_FTYPE) || 
					parts[0].equals(FeatureTypes.HIGHWAY_FEATURE_TYPE)) {
				
				parts = ArrayUtils.remove(parts, parts.length - 1);
				idParam = StringUtils.join(parts, '-');
			}
			else {
				idParam = request.getHeader("row");
			}
		}

		boolean withRelated = "true".equals(request.getHeader("related"));
		
		JSONObject feature = getFeature(idParam, withRelated);
		
		if(feature != null) {
			return feature;
		}
		
		response.setResponseCode(404);
		return null;
	}

	public static JSONObject getFeature(String idParam, boolean withRelated) {
		Client client = ESNodeHodel.getClient();
		
		if(idParam == null) {
			return null;
		}
		
		QueryBuilder q = QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(), 
				FilterBuilders.orFilter(FilterBuilders.termsFilter("feature_id", idParam), FilterBuilders.termsFilter("id", idParam)) );
		
		SearchResponse searchResponse = client.prepareSearch("gazetteer")
			.setSize(10)
			.setQuery(q).get();
		
		SearchHit[] hits = searchResponse.getHits().getHits();
		
		if(hits.length > 0) {
			
			JSONObject feature = mergeIntoFeature(hits);

			if(withRelated) {
				JSONObject related =  getRelated(feature);
				if(related != null) {
					feature.put("_related", related);
				}
			}
			
			return feature;
		}
		
		return null;
	}

	private static JSONObject getRelated(JSONObject feature) {

		String id = feature.getString("feature_id");
		String type = getTypeVerbose(feature);
		
		Set<String> lowerTypes = typesMap.get(type);
		
		if(lowerTypes == null || lowerTypes.isEmpty()) {
			return null;
		}
		
		Client client = ESNodeHodel.getClient();
		
		QueryBuilder q = buildQ(id, lowerTypes); 
		
		SearchRequestBuilder querry = client.prepareSearch("gazetteer")
				.setTypes(Importer.TYPE_NAME)
				.setSize(100)
				.setQuery(q);
		
		addHighlitedFields(querry);
		
		SearchResponse searchResponse = querry
				.execute().actionGet();
		
		JSONObject result = new JSONObject();
		
		Map<String, List<JSONObject>> byTypes = new HashMap<String, List<JSONObject>>();
		for(SearchHit hit : searchResponse.getHits().getHits()) {
			
			JSONObject h = new JSONObject(hit.getSourceAsString());
			
			String typeKey = h.getString("type");
			
			Set<String> fieldSet = hit.getHighlightFields().keySet();
			h.put("_hitFields", new JSONArray(fieldSet));
			
			if(byTypes.get(typeKey) == null) {
				byTypes.put(typeKey, new ArrayList<JSONObject>());
			}
			
			byTypes.get(typeKey).add(h);
		}
		
		for(Entry<String, List<JSONObject>> entry : byTypes.entrySet()) {
			result.put(entry.getKey(), new JSONArray(entry.getValue()));
		}
		
		return result;
	}

	private static String getTypeVerbose(JSONObject feature) {
		
		String typeGeneral = feature.getString("type");
		
		if(typeGeneral.equals(FeatureTypes.POI_FTYPE)) {
			return "poi";
		}

		if(typeGeneral.equals(FeatureTypes.ADDR_POINT_FTYPE)) {
			return "adr";
		}

		if(typeGeneral.equals(FeatureTypes.HIGHWAY_FEATURE_TYPE)) {
			return "highway";
		}

		if(typeGeneral.equals(FeatureTypes.PLACE_BOUNDARY_FTYPE) || typeGeneral.equals(FeatureTypes.PLACE_POINT_FTYPE)) {
			return "place";
		}

		if(typeGeneral.equals(FeatureTypes.ADMIN_BOUNDARY_FTYPE)) {
			JSONArray addresses = feature.optJSONArray("addresses");

			if(addresses.length() > 0) {
				JSONObject addr = addresses.optJSONObject(0);
				
				if(addr.has("place_name")) {
					return "place";
				}

				if(addr.has("local_admin_name")) {
					return "region";
				}

				if(addr.has("admin2_name")) {
					return "region";
				}

				if(addr.has("admin1_name")) {
					return "region";
				}

				if(addr.has("admin0_name")) {
					return "country";
				}
				
			}
		}
		
		return null;
	}

	private static void addHighlitedFields(SearchRequestBuilder querry) {
		querry.addHighlightedField("refs.*");
		querry.addHighlightedField("nearby_streets.*");
		querry.addHighlightedField("nearby_places.*");
		querry.addHighlightedField("nearest_place.*");
		querry.addHighlightedField("nearest_neighbour.*");
		querry.addHighlightedField("nearby_addresses.*");
	}

	private static QueryBuilder buildQ(String id, Collection<String> lowerTypes) {
		
		return QueryBuilders.boolQuery()
			.should(QueryBuilders.queryString("refs.\\*:\"" + id + "\"").boost(10))
			.should(QueryBuilders.queryString("nearby_streets.id:\"" + id + "\""))
			.should(QueryBuilders.queryString("nearby_places.id:\"" + id + "\""))
			.should(QueryBuilders.queryString("nearest_place.id:\"" + id + "\""))
			.should(QueryBuilders.queryString("nearest_neighbour.id:\"" + id + "\""))
			.should(QueryBuilders.queryString("nearby_addresses.\\*:\"" + id + "\""))
			.must(QueryBuilders.termsQuery("type", lowerTypes));
		
	}

	private static JSONObject mergeIntoFeature(SearchHit[] hits) {
		
		JSONObject result = null;
		JSONArray addresses = new JSONArray();
		
		for(SearchHit hitsrc : hits) {
			
			JSONObject hit = new JSONObject(hitsrc.getSourceAsString());
			
			if(result == null) {
				result = new JSONObject(hit, COMMON_FIELDS);
			}
			
			addresses.put(new JSONObject(hit, ADDR_ROW_FIELDS));
		}
		
		if(result != null) {
			result.put("addresses", addresses);
		}
		
		return result;
	}

}
