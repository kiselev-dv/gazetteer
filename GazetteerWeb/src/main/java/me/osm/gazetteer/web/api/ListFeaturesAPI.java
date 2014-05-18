package me.osm.gazetteer.web.api;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;

import me.osm.gazetteer.web.ESNodeHodel;
import me.osm.gazetteer.web.FeatureTypes;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.json.JSONObject;

public class ListFeaturesAPI implements API {
	
	@Override
	public JSONObject request(HttpServletRequest request) 
			throws GazetteerAPIException, IOException {
		
		String[] parents = request.getParameterValues("parent");
		
		if(parents != null) {
			

			QueryBuilder q = getParentsFilter(parents);
			
			String querryText = request.getParameter("q");
			if(querryText != null && StringUtils.isNotEmpty(querryText)) {
				
				String[] typesFilter = request.getParameterValues("type");
				
				BoolQueryBuilder searchQuerry = SearchAPI.getSearchQuerry(querryText);
				q = searchQuerry.must(q);
				
				if(typesFilter != null && typesFilter.length > 0) {
					((BoolQueryBuilder)q).must(QueryBuilders.termsQuery("type", typesFilter));
				}
				
			}
			boolean explain = "true".equals(request.getParameter("explain"));

			Client client = ESNodeHodel.getClient();
			SearchRequestBuilder searchQ = client.prepareSearch("gazetteer")
					.setSearchType(SearchType.QUERY_AND_FETCH).setQuery(q)
					.setExplain(explain);
			
			APIUtils.applyPaging(request, searchQ);
			
			SearchResponse searchResponse = searchQ.execute().actionGet();
			
			JSONObject answer = APIUtils.encodeSearchResult(searchResponse, 
					request.getParameter("full_geometry") != null && "true".equals(request.getParameter("full_geometry")),
					explain);
			
			return answer;
			
		}
		else {
			boolean explain = "true".equals(request.getParameter("explain"));
			
			Client client = ESNodeHodel.getClient();
			BoolQueryBuilder q = QueryBuilders.boolQuery();
			
			q.must(QueryBuilders.termQuery("ftype", FeatureTypes.ADMIN_BOUNDARY_FTYPE));
			q.must(QueryBuilders.termQuery("addr_level", "admin0"));
			
			SearchRequestBuilder searchQ = client.prepareSearch("gazetteer")
					.setSearchType(SearchType.QUERY_AND_FETCH).setQuery(q)
					.setExplain(explain);
			
			APIUtils.applyPaging(request, searchQ);
			
			SearchResponse searchResponse = searchQ.execute().actionGet();
			
			JSONObject answer = APIUtils.encodeSearchResult(searchResponse, 
					request.getParameter("full_geometry") != null && "true".equals(request.getParameter("full_geometry")),
					explain);
			
			return answer;
		}
		
	}
	
	private QueryBuilder getParentsFilter(String[] parents) {
		return QueryBuilders.termsQuery("refs", parents);
	}

}
