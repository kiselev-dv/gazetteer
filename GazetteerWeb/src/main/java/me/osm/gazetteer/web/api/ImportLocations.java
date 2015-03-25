package me.osm.gazetteer.web.api;

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
		boolean osmdoc = "true".equals(request.getHeader("osmdoc"));
		
		if(drop) {
			if(INDICES_CLIENT.exists(new IndicesExistsRequest("gazetteer")).actionGet().isExists()) {
				INDICES_CLIENT.delete(new DeleteIndexRequest("gazetteer")).actionGet();
			}
			
			result.put("drop", true);
		}
		
		boolean imp = StringUtils.isNotEmpty(source);
		if(imp) {
			
			Importer importer = new Importer(source, buildingsGeometry);
			
			if(importer.submit()) {
				result.put("state", "submited");
				result.put("locations_import", imp);
				result.put("task_id", importer.getId());
				result.put("buildings_geometry", buildingsGeometry);
			}
			else {
				result.put("state", "rejected");
				result.put("locations_import", imp);
				result.put("task_id", importer.getId());
				result.put("buildings_geometry", buildingsGeometry);
			}
		}

		if(osmdoc) {
			new ImportOSMDoc().run(null);
			result.put("osmdoc", osmdoc);
		}
		
		return result;
	}
	
}
