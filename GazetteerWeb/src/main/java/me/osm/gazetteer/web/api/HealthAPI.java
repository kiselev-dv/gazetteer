package me.osm.gazetteer.web.api;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import me.osm.gazetteer.web.ESNodeHolder;
import me.osm.gazetteer.web.api.meta.Endpoint;
import me.osm.gazetteer.web.api.meta.health.Health;
import me.osm.gazetteer.web.executions.BackgroundExecutorFacade;
import me.osm.gazetteer.web.imp.IndexHolder;

import org.elasticsearch.action.count.CountRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.domain.metadata.UriMetadata;

public class HealthAPI implements DocumentedApi {
	
	private static final Map<String, String> versions = new HashMap<>();
	
	static { 
		try {
			Properties versionProperties = new Properties();
			versionProperties.load(HealthAPI.class.getResourceAsStream("/version.properties"));
			
			for(Map.Entry<Object, Object> entry : versionProperties.entrySet()) {
				versions.put(entry.getKey().toString(), entry.getValue().toString());
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public Health read(Request req, Response res)	{
		Health health = new Health();
		
		RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
		health.setUptime(rb.getUptime());
		
		int mb = 1024 * 1024;
		Runtime rt = Runtime.getRuntime();
		health.setFreeMemMB(rt.freeMemory() / mb);
		health.setMaxMemMB(rt.maxMemory() / mb);
		
		try {
			Client client = ESNodeHolder.getClient();
			long featuresCount = client.prepareCount("gazetteer")
					.setTypes(IndexHolder.LOCATION)
					.setQuery(QueryBuilders.matchAllQuery())
					.get().getCount();
			health.setFeatures(featuresCount);
			
			
			SearchRequestBuilder types = client.prepareSearch("gazetteer").setTypes(IndexHolder.LOCATION)
					.setQuery(QueryBuilders.matchAllQuery())
					.setSearchType(SearchType.COUNT)
					.addAggregation(AggregationBuilders.terms("ftypes").field("type"))
					.addAggregation(AggregationBuilders.terms("regions").field("_imported.region"));
			
			fillTypeCounters(health, types);
			fillRegions(health, types);
			
			
			CountRequestBuilder poiClasses = client.prepareCount("gazetteer").setTypes(IndexHolder.POI_CLASS)
					.setQuery(QueryBuilders.matchAllQuery());
			
			health.setPoiClasses(poiClasses.get().getCount());
		}
		catch (Exception e) {
			health.setEsnodeError(e.getMessage());
		}
		
		health.setBackgroundTasks(BackgroundExecutorFacade.get().getStateInfo()); 

		health.setVersions(versions);
		
		return health;
	}

	private void fillRegions(Health health, SearchRequestBuilder types) {
		Map<String, Long> regionCounters = new HashMap<>();
		Terms aggregation = types.get().getAggregations().get("regions");
		for(Bucket bucket : aggregation.getBuckets()) {
			regionCounters.put(bucket.getKey(), bucket.getDocCount()); 
		}
		
		health.setRegions(regionCounters);
	}

	private void fillTypeCounters(Health health, SearchRequestBuilder types) {
		Map<String, Long> typesCount = new HashMap<>();
		Terms aggregation = types.get().getAggregations().get("ftypes");
		for(Bucket bucket : aggregation.getBuckets()) {
			typesCount.put(bucket.getKey(), bucket.getDocCount()); 
		}
		
		health.setCounters(typesCount);
	}
	
	@Override
	public Endpoint getMeta(UriMetadata uriMetadata) {
		Endpoint meta = new Endpoint(uriMetadata.getPattern(), "Node health", 
				"Returns information about curent uptime, free mem, e.t.c.");
		
		return meta;
	}
}
