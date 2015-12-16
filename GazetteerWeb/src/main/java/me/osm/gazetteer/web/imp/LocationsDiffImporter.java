package me.osm.gazetteer.web.imp;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.delete.DeleteRequestBuilder;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.json.JSONObject;

public class LocationsDiffImporter extends LocationsDumpImporter {

	public LocationsDiffImporter(String source, boolean buildingsGeometry) {
		super(source, buildingsGeometry);
	}

	@Override
	protected void createRequestAndAdd(String line) {
		
		String action = StringUtils.strip(line.substring(0, 1));
		String json = line.substring(2, line.length() - 1);

		// Remove
		if("-".equals(action)) {
			String id = new JSONObject(json).getString("id");
			deleteRequest(id);
			counter++;
		}
		// Add
		else if("+".equals(action)) {
			String processed = processLine(json);
			
			if(processed != null) {
				IndexRequestBuilder ind = indexRequest(processed);
				bulkRequest.add(ind.request());
				
				counter++;
			}
		}
		// Update
		else if("N".equalsIgnoreCase(action)) {
			String processed = processLine(json);
			
			if(processed != null) {
				IndexRequestBuilder ind = indexRequest(processed);
				bulkRequest.add(ind.request());
				
				counter++;
			}
		}
	}

	private void deleteRequest(String id) {
		
		DeleteRequestBuilder reqB = new DeleteRequestBuilder(client, "gazetteer")
			.setType(IndexHolder.LOCATION).setId(id);
		
		bulkRequest.add(reqB.request());
	}
	
}
