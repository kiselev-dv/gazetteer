package me.osm.gazetteer.web.csvgeocode;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import me.osm.gazetteer.web.GazetteerWeb;
import me.osm.gazetteer.web.api.AnswerDetalization;
import me.osm.gazetteer.web.api.SearchAPI;
import me.osm.gazetteer.web.executions.AbortedException;
import me.osm.gazetteer.web.executions.BackgroudTaskDescription;
import me.osm.gazetteer.web.executions.BackgroundExecutorFacade.BackgroundExecutableTask;
import me.osm.gazetteer.web.imp.LocationsDumpImporter;

import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.supercsv.io.CsvMapReader;
import org.supercsv.io.CsvMapWriter;
import org.supercsv.prefs.CsvPreference;

public class CSVGeocode extends BackgroundExecutableTask {

	private String filePath;
	private String callback;
	
	private SearchAPI searchAPI;
	
	public CSVGeocode(){};
	
	public CSVGeocode(String filePath, String callback, SearchAPI searchAPI) {
		super();
		this.filePath = filePath;
		this.callback = callback;
		this.searchAPI = searchAPI;
	}

	@Override
	public void executeTask() throws AbortedException {
		try {
			CsvMapReader csvMapReader = 
					new CsvMapReader(new InputStreamReader(LocationsDumpImporter.getFileIS(filePath)), 
							CsvPreference.STANDARD_PREFERENCE);
			
			String[] header = csvMapReader.getHeader(true);

			int count = new File(GazetteerWeb.config().getMassGeocodeFolder()).listFiles().length;
			File outFile = new File(GazetteerWeb.config().getMassGeocodeFolder() + File.pathSeparator + count + "csv.gz");
			
			CsvMapWriter csvMapWriter = new CsvMapWriter(new OutputStreamWriter(new GzipCompressorOutputStream(
					new FileOutputStream(outFile))), CsvPreference.STANDARD_PREFERENCE);

			String[] writeHeader = writeHeader(header, csvMapWriter);
			
			Map<String, String> row = null;
			while( (row = csvMapReader.read(header)) != null ) {
				String string = row.get("search_text");

				JSONObject answer = searchAPI.internalSearch(
						false, string, null, null, null, null, 
						null, true, false, true, 
						AnswerDetalization.SHORT, null);
				
				fillTheRow(row, answer);
				
				csvMapWriter.write(row, writeHeader);
			}
			
			csvMapWriter.flush();
			csvMapWriter.close();
			
		}
		catch (Exception e) {
			throw new AbortedException(e.getMessage(), e, false); 
		}
	}

	private void fillTheRow(Map<String, String> row, JSONObject answer) {
		
		String lat = null;
		String lon = null;
		String score = null;
		String lvl = null;
		String id = null;

		JSONArray features = answer.optJSONArray("features");
		
		if(features != null) {
			JSONObject firstAnswer = features.optJSONObject(0);
			
			if(firstAnswer != null) {
				id = firstAnswer.optString("id");
				JSONObject cp = firstAnswer.optJSONObject("center_point");
				if(cp != null) {
					lat = String.valueOf(cp.optDouble("lat", Double.NaN)); 
					lon = String.valueOf(cp.optDouble("lon", Double.NaN)); 
				}
				score = String.valueOf(firstAnswer.opt("_hit_score"));
				lvl = String.valueOf(firstAnswer.optString("weight_base_type"));
			}
		}
		
		row.put("result_lat", lat);
		row.put("result_lon", lon);
		row.put("result_score", score);
		row.put("result_lvl", lvl);
		row.put("result_id", id);
	}

	private String[] writeHeader(String[] header, CsvMapWriter csvMapWriter)
			throws IOException {
		
		List<String> outHeader =  new ArrayList<>(Arrays.asList(header));
		if(!outHeader.contains("result_lat")) {
			outHeader.add("result_lat");
		}
		if(!outHeader.contains("result_lon")) {
			outHeader.add("result_lat");
		}
		if(!outHeader.contains("result_score")) {
			outHeader.add("result_score");
		}
		if(!outHeader.contains("result_lvl")) {
			outHeader.add("result_lvl");
		}
		if(!outHeader.contains("result_id")) {
			outHeader.add("result_id");
		}
		
		String[] array = outHeader.toArray(new String[outHeader.size()]);
		csvMapWriter.writeHeader(array);
		
		return array;
	}

	@Override
	public BackgroudTaskDescription description() {
		BackgroudTaskDescription description = new BackgroudTaskDescription();
		
		description.setId(this.getId());
		
		description.setClassName(getClass().getName());
		Map<String, Object> parameters = new HashMap<String, Object>();
		description.setParameters(parameters);
		
		parameters.put("source", filePath);
		parameters.put("callback", callback);
		
		return description;
	}

	public String getFilePath() {
		return filePath;
	}

	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}

	public String getCallback() {
		return callback;
	}

	public void setCallback(String callback) {
		this.callback = callback;
	}

}
