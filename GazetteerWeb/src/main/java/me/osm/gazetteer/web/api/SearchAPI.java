package me.osm.gazetteer.web.api;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import me.osm.gazetteer.web.ESNodeHodel;
import me.osm.gazetteer.web.api.API.GazetteerAPIException;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.json.JSONArray;
import org.json.JSONObject;

public class SearchAPI implements API {

	
	@Override
	public JSONObject request(HttpServletRequest request) 
			throws GazetteerAPIException, IOException {

		boolean explain = "true".equals(request.getParameter("explain"));
		String querry = request.getParameter("q");
		String[] typesFilter = request.getParameterValues("type");
		
		String[] mainFields = new String[]{"name", "address", "poi_class", "poi_class_names", "operator", "brand"};
		String[] secondaryFields = new String[]{"parts", "alt_addresses", "alt_names"};
		String[] tertiaryFields = new String[]{"nearby_streets", "nearest_city", "nearest_neighbour"};
		
		BoolQueryBuilder q = QueryBuilders.boolQuery()
			.should(QueryBuilders.multiMatchQuery(querry, mainFields).boost(100))
			.should(QueryBuilders.multiMatchQuery(querry, secondaryFields).boost(80))
			.should(QueryBuilders.multiMatchQuery(querry, tertiaryFields).boost(60));
		
		if(typesFilter != null && typesFilter.length > 0) {
			q.must(QueryBuilders.termsQuery("type", typesFilter));
		}
		
		Client client = ESNodeHodel.getClient();
		SearchResponse searchResponse = client.prepareSearch("gazetteer")
			.setSearchType(SearchType.QUERY_AND_FETCH).setSize(20)
			.setQuery(q).setExplain(explain)
			.execute().actionGet();

		JSONObject answer = encodeSearchResult(searchResponse, 
				request.getParameter("full_geometry") != null && "true".equals(request.getParameter("full_geometry")),
				explain);
		
		return answer;
	}

	private JSONObject encodeSearchResult(SearchResponse searchResponse, 
			boolean fullGeometry, boolean explain) {
		
		JSONObject result = new JSONObject();
		result.put("result", "success");
		
		JSONArray features = new JSONArray();
		result.put("features", features);
		
		for(SearchHit hit : searchResponse.getHits().getHits()) {
			JSONObject feature = new JSONObject(hit.getSource());
			
			if(!fullGeometry) {
				feature.remove("full_geometry");
			}
			
			features.put(feature);
		}
		
		if(explain) {
			JSONArray explanations = new JSONArray();
			result.put("explanations", explanations);

			for(SearchHit hit : searchResponse.getHits().getHits()) {
				JSONObject explanation = new JSONObject(hit.explanation().toString());
				
				explanations.put(explanation);
			}
		}
		
		return result;
	}
}
