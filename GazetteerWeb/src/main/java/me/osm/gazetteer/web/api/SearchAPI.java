package me.osm.gazetteer.web.api;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.osm.gazetteer.web.ESNodeHodel;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.json.JSONObject;
import org.restexpress.Request;
import org.restexpress.Response;

public class SearchAPI {

	private static final String[] mainFields = new String[]{"name", "address", "poi_class", "poi_class_names", "operator", "brand"};
	private static final String[] secondaryFields = new String[]{"parts", "alt_addresses", "alt_names"};
	private static final String[] tertiaryFields = new String[]{"nearby_streets", "nearest_city", "nearest_neighbour"};
	
	public JSONObject read(Request request, Response response) 
			throws IOException {

		boolean explain = "true".equals(request.getHeader("explain"));
		String querry = request.getHeader("q");
		if(querry != null) {
			
			Set<String> types = new HashSet<String>();
			List<String> t = request.getHeaders("type");
			if(t != null) {
				for(String s : t) {
					types.addAll(Arrays.asList(StringUtils.split(s, ", []\"\'")));
				}
			}
			
			BoolQueryBuilder q = getSearchQuerry(querry);
			
			if(!types.isEmpty()) {
				q.must(QueryBuilders.termsQuery("type", types));
			}
			
			Client client = ESNodeHodel.getClient();
			SearchRequestBuilder searchQ = client.prepareSearch("gazetteer")
					.setQuery(q)
					.setExplain(explain);
			
			APIUtils.applyPaging(request, searchQ);
			
			searchQ.setMinScore(0.001f);
			
			SearchResponse searchResponse = searchQ.execute().actionGet();
			
			JSONObject answer = APIUtils.encodeSearchResult(searchResponse, 
					request.getHeader("full_geometry") != null && "true".equals(request.getParameter("full_geometry")),
					explain);
			
			APIUtils.resultPaging(request, answer);
			
			return answer;
		}
		
		return null;
	}

	public static BoolQueryBuilder getSearchQuerry(String querry) {
		BoolQueryBuilder q = QueryBuilders.boolQuery()
				.should(buildAddRowQ(querry).boost(20))
				.should(QueryBuilders.multiMatchQuery(querry, mainFields).boost(10))
				.should(QueryBuilders.multiMatchQuery(querry, secondaryFields).boost(9))
				.should(QueryBuilders.multiMatchQuery(querry, tertiaryFields).boost(8));
		return q;
	}

	public static BoolQueryBuilder buildAddRowQ(String querry) {
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

	
}
