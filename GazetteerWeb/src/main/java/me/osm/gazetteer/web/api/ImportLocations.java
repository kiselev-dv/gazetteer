package me.osm.gazetteer.web.api;

import me.osm.gazetteer.web.ESNodeHolder;
import me.osm.gazetteer.web.api.meta.Endpoint;
import me.osm.gazetteer.web.api.meta.Parameter;
import me.osm.gazetteer.web.api.utils.ImportSrcType;
import me.osm.gazetteer.web.api.utils.RequestUtils;
import me.osm.gazetteer.web.imp.IndexHolder;
import me.osm.gazetteer.web.imp.LocationsDiffImporter;
import me.osm.gazetteer.web.imp.LocationsDumpImporter;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.elasticsearch.action.delete.DeleteRequestBuilder;
import org.elasticsearch.action.deletebyquery.DeleteByQueryRequestBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.json.JSONObject;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.domain.metadata.UriMetadata;

/**
 * Import data (addresses, poi, highways and so on).
 * */
public class ImportLocations implements DocumentedApi {

	/**
	 * Path to file
	 * */
	private static final String SOURCE_HEADER = "source";

	/**
	 * Type of file, dump or diff
	 * */
	private static final String TYPE_HEADER = "source_type";
	
	/**
	 * Drop index before import
	 * */
	private static final String DROP_HEADER = "drop";
	
	/**
	 * Import building full geometry (true by default)
	 * */
	private static final String BUILDINGS_GEOMETRY_HEADER = "buildings_geometry";

	/**
	 * Call http get on this url after import is done
	 * */
	private static final String CALLBACK_HEADER = "callback_url";
	
	/**
	 * Also import osmdoc
	 * */
	private static final String OSMDOC_HEADER = "osmdoc";
	
	
	public JSONObject read(Request request, Response response) {
		
		JSONObject result = new JSONObject();
		
		String source = request.getHeader(SOURCE_HEADER);
		boolean drop = RequestUtils.getBooleanHeader(request, DROP_HEADER, false);
		boolean buildingsGeometry = RequestUtils.getBooleanHeader(request, BUILDINGS_GEOMETRY_HEADER, true);
		boolean osmdoc = RequestUtils.getBooleanHeader(request, OSMDOC_HEADER, false);
		ImportSrcType type = RequestUtils.getEnumHeader(
				request, TYPE_HEADER, ImportSrcType.class, ImportSrcType.DUMP);
		
		String callbackUrl = request.getHeader(CALLBACK_HEADER);
		
		if(drop) {
			new DeleteByQueryRequestBuilder(ESNodeHolder.getClient()).setIndices("gazetteer")
				.setTypes(IndexHolder.LOCATION).setQuery(QueryBuilders.matchAllQuery()).execute().actionGet();
			
			result.put(DROP_HEADER, true);
		}
		
		if(osmdoc) {
			new ImportOSMDoc().run(null, drop);
			result.put(OSMDOC_HEADER, osmdoc);
		}
		
		boolean imp = StringUtils.isNotEmpty(source);
		if(imp) {
			
			LocationsDumpImporter importer = null;
			if(type == ImportSrcType.DIFF) {
				importer = new LocationsDiffImporter(source, buildingsGeometry);
			}
			else {
				importer = new LocationsDumpImporter(source, buildingsGeometry);
			}
			
			if(StringUtils.isNotEmpty(callbackUrl) && isValidUrl(callbackUrl)) {
				importer.setCallback(callbackUrl);
				result.put("callback_url", callbackUrl);
			}
			
			if(importer.submit()) {
				result.put("state", "submitted");
				result.put("locations_import", imp);
				result.put("task_id", importer.getId());
				result.put("task_uuid", importer.getUUID());
				result.put(BUILDINGS_GEOMETRY_HEADER, buildingsGeometry);
			}
			else {
				result.put("state", "rejected");
				result.put("locations_import", imp);
				result.put("task_id", importer.getId());
				result.put("task_uuid", importer.getUUID());
				result.put(BUILDINGS_GEOMETRY_HEADER, buildingsGeometry);
			}
		}

		return result;
	}

	//TODO move to utils
	public static boolean isValidUrl(String callbackUrl) {
		return new UrlValidator(UrlValidator.ALLOW_2_SLASHES + UrlValidator.ALLOW_LOCAL_URLS).isValid(callbackUrl);
	}

	@Override
	public Endpoint getMeta(UriMetadata uriMetadata) {
		Endpoint meta = new Endpoint(uriMetadata.getPattern(), "locations import", 
				"Import data (addresses, poi, highways and so on). "
			  + "Import is asynchronous. "
			  + "So call just returns you a task id and parsed parameters. "
			  + "This is endpoin is protected with HTTP Base Auth. "
			  + "You could setup password via app config.");
		
		
		meta.getUrlParameters().add(new Parameter(SOURCE_HEADER, "Path to dump file. "
				+ "Relative to working dir or absolute. "
				+ "If file ends with .gz it will be unzipped automaticaly."));
		meta.getUrlParameters().add(new Parameter(TYPE_HEADER, 
				"Type of source file, DUMP or DIFF. Default value is DUMP."));
		meta.getUrlParameters().add(new Parameter(DROP_HEADER, 
				"Drop exists index before import. false by default."));
		meta.getUrlParameters().add(new Parameter(BUILDINGS_GEOMETRY_HEADER, 
				"Import buildings geometry for POIs and Addresses. true by default."));
		meta.getUrlParameters().add(new Parameter(OSMDOC_HEADER, 
				"Also import osmdoc (will uses embedded version of osmdoc catalog)."));
		
		return meta;
	}
	
}
