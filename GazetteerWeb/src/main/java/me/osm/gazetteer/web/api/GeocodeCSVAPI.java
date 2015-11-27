package me.osm.gazetteer.web.api;

import static me.osm.gazetteer.web.api.utils.RequestUtils.getSet;

import java.util.Set;

import me.osm.gazetteer.web.api.meta.Endpoint;
import me.osm.gazetteer.web.csvgeocode.CSVGeocode;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.domain.metadata.UriMetadata;

/**
 * Geocode lines from csv file
 * 
 * @author dkiselev
 */
public class GeocodeCSVAPI implements DocumentedApi {
	
	private SearchAPI searchAPI;
	
	public GeocodeCSVAPI(SearchAPI searchAPI) {
		super();
		this.searchAPI = searchAPI;
	}

	public Object read(Request request, Response res) {
		
		JSONObject result = new JSONObject();
		
		String source = request.getHeader("source");
		String callbackUrl = request.getHeader("callback_url");
		Set<String> refs = getSet(request, SearchAPI.REFERENCES_HEADER);
		
		boolean imp = StringUtils.isNotEmpty(source);
		if(imp) {
			
			CSVGeocode importer = new CSVGeocode(source, callbackUrl, searchAPI);
			importer.setRefs(refs);
			
			if(StringUtils.isNotEmpty(callbackUrl) && 
					ImportLocations.isValidUrl(callbackUrl)) {
				
				importer.setCallback(callbackUrl);
				result.put("callback_url", callbackUrl);
			}
			
			if(importer.submit()) {
				result.put("state", "submited");
				result.put("task_id", importer.getId());
			}
			else {
				result.put("state", "rejected");
				result.put("task_id", importer.getId());
			}
		}
		
		return result;
	}

	@Override
	public Endpoint getMeta(UriMetadata uriMetadata) {
		Endpoint meta = new Endpoint(uriMetadata.getPattern(), "Geocode scv file", 
				"Geocode lines from csv file.");
		
		return meta;
	}
}
