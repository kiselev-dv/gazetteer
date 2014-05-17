package me.osm.gazetteer.web.api;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;

import me.osm.gazetteer.web.ESNodeHodel;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.json.JSONArray;
import org.json.JSONObject;

public class SearchAPI implements API {

	private static final String[] mainFields = new String[]{"name", "address", "poi_class", "poi_class_names", "operator", "brand"};
	private static final String[] secondaryFields = new String[]{"parts", "alt_addresses", "alt_names"};
	private static final String[] tertiaryFields = new String[]{"nearby_streets", "nearest_city", "nearest_neighbour"};
	
	@Override
	public JSONObject request(HttpServletRequest request) 
			throws GazetteerAPIException, IOException {

		boolean explain = "true".equals(request.getParameter("explain"));
		String querry = request.getParameter("q");
		if(querry != null) {
			
			String[] typesFilter = request.getParameterValues("type");
			
			
			BoolQueryBuilder q = QueryBuilders.boolQuery()
					.should(buildAddRowQ(querry).boost(20))
					.should(QueryBuilders.multiMatchQuery(querry, mainFields).boost(10))
					.should(QueryBuilders.multiMatchQuery(querry, secondaryFields).boost(9))
					.should(QueryBuilders.multiMatchQuery(querry, tertiaryFields).boost(8));
			
			if(typesFilter != null && typesFilter.length > 0) {
				q.must(QueryBuilders.termsQuery("type", typesFilter));
			}
			
			Client client = ESNodeHodel.getClient();
			SearchRequestBuilder searchQ = client.prepareSearch("gazetteer")
					.setSearchType(SearchType.QUERY_AND_FETCH).setQuery(q)
					.setExplain(explain);
			
			applyPaging(request, searchQ);
			
			SearchResponse searchResponse = searchQ.execute().actionGet();
			
			JSONObject answer = encodeSearchResult(searchResponse, 
					request.getParameter("full_geometry") != null && "true".equals(request.getParameter("full_geometry")),
					explain);
			
			return answer;
		}
		
		return null;
	}

	private void applyPaging(HttpServletRequest request,
			SearchRequestBuilder searchQ) {
		int pageSize = 20;
		if(request.getParameter("pagesize") != null) {
			pageSize = Integer.parseInt(request.getParameter("pagesize"));
		}
		
		int page = 1;
		if(request.getParameter("page") != null) {
			page = Integer.parseInt(request.getParameter("page"));
			if(page < 1) {
				page = 1;
			}
		}
		searchQ.setSize(pageSize);
		searchQ.setFrom((page - 1) * pageSize);
	}

	private BoolQueryBuilder buildAddRowQ(String querry) {
		return QueryBuilders.boolQuery()
			.should(QueryBuilders.matchQuery("admin0_name", querry).boost(1))
			.should(QueryBuilders.matchQuery("admin0_alternate_names", querry).boost(0.1f))
			.should(QueryBuilders.matchQuery("admin1_name", querry).boost(2))
			.should(QueryBuilders.matchQuery("admin1_alternate_names", querry).boost(0.2f))
			.should(QueryBuilders.matchQuery("admin2_name", querry).boost(3))
			.should(QueryBuilders.matchQuery("admin2_alternate_names", querry).boost(0.3f))
			.should(QueryBuilders.matchQuery("local_admin_name", querry).boost(4))
			.should(QueryBuilders.matchQuery("local_admin_alternate_names", querry).boost(0.4f))
			.should(QueryBuilders.matchQuery("locality_name", querry).boost(5))
			.should(QueryBuilders.matchQuery("locality_alternate_names", querry).boost(0.4f))
			.should(QueryBuilders.matchQuery("neighborhood_name", querry).boost(6))
			.should(QueryBuilders.matchQuery("neighborhood_alternate_names", querry).boost(0.6f))
			.should(QueryBuilders.matchQuery("street_name", querry).boost(7))
			.should(QueryBuilders.matchQuery("housenumber", querry).boost(100));
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
				explanations.put(hit.explanation().toHtml());
			}
		}
		
		return result;
	}
}
