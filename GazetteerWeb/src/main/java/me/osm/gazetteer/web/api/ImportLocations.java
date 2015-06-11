package me.osm.gazetteer.web.api;

import me.osm.gazetteer.web.ESNodeHodel;
import me.osm.gazetteer.web.api.meta.Endpoint;
import me.osm.gazetteer.web.api.meta.Parameter;
import me.osm.gazetteer.web.api.utils.RequestUtils;
import me.osm.gazetteer.web.imp.Importer;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest;
import org.elasticsearch.client.IndicesAdminClient;
import org.json.JSONObject;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.domain.metadata.UriMetadata;

/**
 * Import data (addresses, poi, highways and so on).
 * */
public class ImportLocations implements DocumentedApi {

	private static final IndicesAdminClient INDICES_CLIENT = ESNodeHodel.getClient().admin().indices();
	
	/**
	 * Path to dump file
	 * */
	private static final String SOURCE_HEADER = "source";
	
	/**
	 * Drop index before import
	 * */
	private static final String DROP_HEADER = "drop";
	
	/**
	 * Import building full geometry (true by default)
	 * */
	private static final String BUILDINGS_GEOMETRY_HEADER = "buildings_geometry";
	
	/**
	 * Also import osmdoc
	 * */
	private static final String OSMDOC_HEADER = "osmdoc";
	
	public JSONObject read(Request request, Response response){
		
		JSONObject result = new JSONObject();
		
		String source = request.getHeader(SOURCE_HEADER);
		boolean drop = RequestUtils.getBooleanHeader(request, DROP_HEADER, false);
		boolean buildingsGeometry = RequestUtils.getBooleanHeader(request, BUILDINGS_GEOMETRY_HEADER, true);
		boolean osmdoc = RequestUtils.getBooleanHeader(request, OSMDOC_HEADER, false);
		
		if(drop) {
			if(INDICES_CLIENT.exists(new IndicesExistsRequest("gazetteer")).actionGet().isExists()) {
				INDICES_CLIENT.delete(new DeleteIndexRequest("gazetteer")).actionGet();
			}
			
			result.put(DROP_HEADER, true);
		}
		
		boolean imp = StringUtils.isNotEmpty(source);
		if(imp) {
			
			Importer importer = new Importer(source, buildingsGeometry);
			
			if(importer.submit()) {
				result.put("state", "submited");
				result.put("locations_import", imp);
				result.put("task_id", importer.getId());
				result.put(BUILDINGS_GEOMETRY_HEADER, buildingsGeometry);
			}
			else {
				result.put("state", "rejected");
				result.put("locations_import", imp);
				result.put("task_id", importer.getId());
				result.put(BUILDINGS_GEOMETRY_HEADER, buildingsGeometry);
			}
		}

		if(osmdoc) {
			new ImportOSMDoc().run(null);
			result.put(OSMDOC_HEADER, osmdoc);
		}
		
		return result;
	}

	@Override
	public Endpoint getMeta(UriMetadata uriMetadata) {
		Endpoint meta = new Endpoint(uriMetadata.getPattern(), "locations import", 
				"Import data (addresses, poi, highways and so on). "
			  + "Import is asynchronous. "
			  + "So call just returns you a task id and parsed parameters. "
			  + "This is endpoin is protected with HTTP Base Auth. "
			  + "You could setup password via app config.");
		
		meta.getPathParameters().add(new Parameter(SOURCE_HEADER, "Path to dump file. "
				+ "Relative to working dir or absolute. "
				+ "If file ends with .gz it will be unzipped automaticaly."));

		meta.getPathParameters().add(new Parameter(DROP_HEADER, 
				"Drop exists index before import. false by default."));
		meta.getPathParameters().add(new Parameter(BUILDINGS_GEOMETRY_HEADER, 
				"Import buildings geometry for POIs and Addresses. true by default."));
		meta.getPathParameters().add(new Parameter(OSMDOC_HEADER, 
				"Also import osmdoc (will uses embedded version of osmdoc catalog)."));
		
		return meta;
	}
	
}
