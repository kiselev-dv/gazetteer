package me.osm.gazetteer.web.api;

import java.util.Map.Entry;

import me.osm.gazetteer.web.ESNodeHolder;
import me.osm.gazetteer.web.imp.IndexHolder;

import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsRequestBuilder;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.hppc.ObjectContainer;
import org.elasticsearch.common.hppc.cursors.ObjectCursor;
import org.json.JSONObject;
import org.restexpress.Request;
import org.restexpress.Response;

public class IndexAPI {
	
	public JSONObject read(Request request, Response response) {
		
		boolean rebuild = "true".equals(request.getHeader("rebuild"));
		
		if(rebuild) {
			IndexHolder.dropIndex();
			IndexHolder.createIndex();
			
			JSONObject res = new JSONObject();;
			res.put("result", "done");
			return res;
		}
		else {
			GetMappingsResponse mappingsResponse = new GetMappingsRequestBuilder(
					ESNodeHolder.getClient().admin().indices(), "gazetteer").get();
			
			JSONObject result = new JSONObject();
			
			ImmutableOpenMap<String, ImmutableOpenMap<String, MappingMetaData>> answer = mappingsResponse.getMappings();
			ObjectContainer<MappingMetaData> mappings = answer.get("gazetteer").values();
			for (ObjectCursor<MappingMetaData> objectCursor : mappings) {
				MappingMetaData mmeta = objectCursor.value;
				result.put(mmeta.type(), new JSONObject(mmeta.source().toString()));
			}
			
			return result;
		}
		
	}
}
