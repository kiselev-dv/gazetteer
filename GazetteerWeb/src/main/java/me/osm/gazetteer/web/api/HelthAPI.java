package me.osm.gazetteer.web.api;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.HashMap;
import java.util.Map;

import me.osm.gazetteer.web.ESNodeHodel;
import me.osm.gazetteer.web.api.meta.Endpoint;
import me.osm.gazetteer.web.api.meta.health.Health;
import me.osm.gazetteer.web.executions.BackgroundExecutorFacade;
import me.osm.gazetteer.web.imp.IndexHolder;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.domain.metadata.UriMetadata;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;

public class HelthAPI implements DocumentedApi {

	public Health read(Request req, Response res)	{
		Health health = new Health();
		
		RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
		health.setUptime(rb.getUptime());
		
		int mb = 1024 * 1024;
		Runtime rt = Runtime.getRuntime();
		health.setFreeMemMB(rt.freeMemory() / mb);
		health.setMaxMemMB(rt.maxMemory() / mb);
		
		Client client = ESNodeHodel.getClient();
		long featuresCount = client.prepareCount("gazetteer")
				.setTypes(IndexHolder.LOCATION)
				.setQuery(QueryBuilders.matchAllQuery())
				.get().getCount();
		health.setFeatures(featuresCount);
		
		
		SearchRequestBuilder types = client.prepareSearch("gazetteer").setTypes(IndexHolder.LOCATION)
			.setQuery(QueryBuilders.matchAllQuery())
			.setSearchType(SearchType.COUNT)
			.addAggregation(AggregationBuilders.terms("ftypes").field("type"));
		
		Map<String, Long> typesCount = new HashMap<>();
		Terms aggregation = types.get().getAggregations().get("ftypes");
		for(Bucket bucket : aggregation.getBuckets()) {
			typesCount.put(bucket.getKey(), bucket.getDocCount()); 
		}
		
		health.setCounters(typesCount);
		
		health.setBackgroundTasks(BackgroundExecutorFacade.get().getStateInfo()); 
		
		return health;
	}
	
	@Override
	public Endpoint getMeta(UriMetadata uriMetadata) {
		Endpoint meta = new Endpoint(uriMetadata.getPattern(), "Node health", 
				"Returns information about curent uptime, free mem, e.t.c.");
		
		return meta;
	}
}
