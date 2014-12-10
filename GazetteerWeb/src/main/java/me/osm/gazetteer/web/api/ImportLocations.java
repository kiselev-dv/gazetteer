package me.osm.gazetteer.web.api;

import java.util.concurrent.Executors;

import me.osm.gazetteer.web.ESNodeHodel;
import me.osm.gazetteer.web.imp.Importer;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest;
import org.elasticsearch.client.IndicesAdminClient;
import org.json.JSONObject;
import org.restexpress.Request;
import org.restexpress.Response;

public class ImportLocations {
	
	private static final IndicesAdminClient INDICES_CLIENT = ESNodeHodel.getClient().admin().indices();
	
	public JSONObject read(Request request, Response response){
		
		JSONObject result = new JSONObject();
		
		String source = request.getHeader("source");
		boolean drop = "true".equals(request.getHeader("drop"));
		boolean buildingsGeometry = !"false".equals(request.getHeader("buildings_geometry"));
		
		if(drop) {
			if(INDICES_CLIENT.exists(new IndicesExistsRequest("gazetteer")).actionGet().isExists()) {
				INDICES_CLIENT.delete(new DeleteIndexRequest("gazetteer")).actionGet();
			}
			
			result.put("drop", true);
		}
		
		if(StringUtils.isNotEmpty(source)) {
			try {
				Executors.callable(new Importer(source, buildingsGeometry)).call();
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			result.put("import", true);
			result.put("buildings_geometry", buildingsGeometry);
		}
		
		return result;
	}
	
}
