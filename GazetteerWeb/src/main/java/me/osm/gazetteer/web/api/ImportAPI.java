package me.osm.gazetteer.web.api;

import me.osm.gazetteer.web.ESNodeHodel;
import me.osm.gazetteer.web.imp.Importer;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequestBuilder;
import org.json.JSONObject;
import org.restexpress.Request;
import org.restexpress.Response;

public class ImportAPI {
	
	public JSONObject read(Request request, Response response){
		
		JSONObject result = new JSONObject();
		
		String source = request.getHeader("source");
		boolean drop = "true".equals(request.getHeader("drop"));
		
		if(drop) {
			new DeleteIndexRequestBuilder(ESNodeHodel.getClient().admin().indices(), "gazetteer").get();
			
			result.put("drop", true);
		}
		
		if(StringUtils.isNotEmpty(source)) {
			new Importer(source).run();
			result.put("import", true);
		}
		
		return result;
	}
	
}
