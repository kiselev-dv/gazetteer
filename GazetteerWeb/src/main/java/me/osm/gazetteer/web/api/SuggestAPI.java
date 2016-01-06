package me.osm.gazetteer.web.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import me.osm.gazetteer.web.ESNodeHolder;
import me.osm.gazetteer.web.GazetteerWeb;
import me.osm.gazetteer.web.api.meta.Endpoint;
import me.osm.gazetteer.web.api.query.Query;
import me.osm.gazetteer.web.api.search.SearchBuilder;
import me.osm.gazetteer.web.api.utils.BuildSearchQContext;
import me.osm.gazetteer.web.api.utils.RequestUtils;
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
import org.restexpress.domain.metadata.UriMetadata;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;


public class SuggestAPI extends SearchAPI {

	@Inject
	private SearchBuilder searchBuilder;
	
	public SuggestAPI() {
		super();
		GazetteerWeb.injector().injectMembers(this);
	}
	
	@Override
	public JSONObject read(Request request, Response response)
			throws IOException {
		
		String querryString = StringUtils.stripToNull(request.getHeader(Q_HEADER));
		Query query = queryAnalyzer.getQuery(querryString);

		boolean addressesOnly = RequestUtils.getBooleanHeader(request, SearchAPI.ADDRESSES_ONLY_HEADER, false);
		
		@SuppressWarnings("unchecked")
		List<JSONObject> type = addressesOnly ? Collections.EMPTY_LIST : suggestPoiType(query);

		JSONObject answer = super.read(request, response);
		
		answer.put("matched_type", new JSONArray(type));
		
		return answer;
	}
	
	@Override
	public BoolQueryBuilder getSearchQuerry(Query querry, boolean strict, BuildSearchQContext context) {
		
		
		BoolQueryBuilder searchQuerry = QueryBuilders.boolQuery();
		
		QueryBuilder prefQ = null;
		
		Query tail = querry.tail();
		Query head = querry.head();
		
		if(tail.countNumeric() == 1) {
			searchBuilder.mainSearchQ(querry, searchQuerry, strict, context);
		}
		else {
			
			String prefix = tail.toString();
			
			prefQ = QueryBuilders.disMaxQuery()
					.add(QueryBuilders.termQuery("housenumber", prefix))
					.add(QueryBuilders.prefixQuery("search", prefix))
					.add(QueryBuilders.prefixQuery("name.text", prefix));
			
			LoggerFactory.getLogger(getClass()).info("Prefix: {}", prefix);
			
			
			if(head == null) {
				searchQuerry.must(prefQ)
					.mustNot(QueryBuilders.termQuery("weight", 0));
			}
			else {
				searchBuilder.mainSearchQ(head, searchQuerry, strict, context);
				searchQuerry.must(prefQ);
			}
		}

		return searchQuerry;
		
	}

	private List<JSONObject> suggestPoiType(Query query) {
		Client client = ESNodeHolder.getClient();
		// TODO: process with replacers
		Query filtered = query.filter(new HashSet<String>(Arrays.asList("на", "дом")));
		
		DisMaxQueryBuilder dismax = QueryBuilders.disMaxQuery()
				.add(QueryBuilders.prefixQuery("translated_title", filtered.toString()));
		
		dismax.add(
				QueryBuilders.boolQuery()
					.should(QueryBuilders.prefixQuery("translated_title", query.tail().toString()))
					.should(QueryBuilders.prefixQuery("keywords", query.tail().toString()))
					.minimumNumberShouldMatch(1)
				);
		
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
	
	@Override
	public Endpoint getMeta(UriMetadata uriMetadata) {
		Endpoint meta = super.getMeta(uriMetadata);
		
		meta.setName("Suggest location");
		meta.setDescription("Performs prefix 'search on type' search among locations.");
		
		return meta;
	}
	
}
