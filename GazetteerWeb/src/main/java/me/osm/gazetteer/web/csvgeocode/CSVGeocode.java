package me.osm.gazetteer.web.csvgeocode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.osm.gazetteer.web.GazetteerWeb;
import me.osm.gazetteer.web.api.AnswerDetalization;
import me.osm.gazetteer.web.api.SearchAPI;
import me.osm.gazetteer.web.executions.AbortedException;
import me.osm.gazetteer.web.executions.BackgroudTaskDescription;
import me.osm.gazetteer.web.executions.BackgroundExecutorFacade.BackgroundExecutableTask;
import me.osm.gazetteer.web.imp.LocationsDumpImporter;

import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;
import org.supercsv.io.CsvMapReader;
import org.supercsv.io.CsvMapWriter;
import org.supercsv.prefs.CsvPreference;

public class CSVGeocode extends BackgroundExecutableTask {

	private String filePath;
	private String callback;

	private String searchField = "search_text";
	
	private SearchAPI searchAPI;
	private Set<String> refs;
	private File outFile = null;
	private int counter;
	
	public CSVGeocode(){};
	
	public CSVGeocode(String filePath, String callback, 
			SearchAPI searchAPI, String searchField) {
		super();
		this.filePath = filePath;
		this.callback = callback;
		this.searchAPI = searchAPI;
		
		this.counter = 0;
		
		if(StringUtils.isNotEmpty(searchField)) {
			this.searchField = searchField;
		}
		
		File geocodeFolder = new File(GazetteerWeb.config().getMassGeocodeFolder());
		geocodeFolder.mkdirs();
		this.outFile = new File(geocodeFolder, getUUID() + ".csv.gz");
	}

	@Override
	public void executeTask() throws AbortedException {
		try {
			
			
			CsvPreference csvPreferences = CsvPreference.STANDARD_PREFERENCE;
			if(StringUtils.endsWith(filePath, ".tsv")) {
				csvPreferences = CsvPreference.TAB_PREFERENCE;
			}
			
			CsvMapReader csvMapReader = 
					new CsvMapReader(new InputStreamReader(LocationsDumpImporter.getFileIS(filePath), 
							Charset.forName("UTF-8")), csvPreferences);
			
			String[] header = csvMapReader.getHeader(true);

			CsvMapWriter csvMapWriter = new CsvMapWriter(new OutputStreamWriter(new GzipCompressorOutputStream(
					new FileOutputStream(outFile)), Charset.forName("UTF-8")), csvPreferences);

			String[] writeHeader = writeHeader(header, csvMapWriter);
			
			Map<String, String> row = null;
			while( (row = csvMapReader.read(header)) != null ) {
				String string = row.get(searchField);

				AnswerDetalization detalization = AnswerDetalization.FULL;
				
				try {
					JSONObject answer = searchAPI.internalSearch(
							false, string, null, null, null, null, 
							this.refs, true, false, true, 
							detalization, null, null);
					
					counter++;
					
					if(!gotResult(answer)) {
						Set<String> types = new HashSet<>(
								Arrays.asList("hghnet", "hghway", "admbnd", "plcpnt"));
						
						answer = searchAPI.internalSearch(
								false, string, types, null, null, null, 
								this.refs, false, false, true, 
								detalization, null, null);
					}
					
					fillTheRow(row, answer);
					
					csvMapWriter.write(row, writeHeader);
				}
				catch (Exception e) {
					LoggerFactory.getLogger(getClass()).error("Failed to geocode {}", string, e);
				}
			}
			
			csvMapWriter.flush();
			csvMapWriter.close();
			
		}
		catch (Exception e) {
			throw new AbortedException(e.getMessage(), e, false); 
		}
	}

	private boolean gotResult(JSONObject answer) {
		JSONArray optJSONArray = answer.optJSONArray("features");
		if(optJSONArray == null) {
			return false;
		}
		if(optJSONArray.length() == 0) {
			return false;
		}
		return true;
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
			outHeader.add("result_lon");
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
		description.setUuid(this.getUUID());
		
		description.setClassName(getClass().getName());
		Map<String, Object> parameters = new HashMap<String, Object>();
		description.setParameters(parameters);
		
		parameters.put("source", filePath);
		parameters.put("callback", callback);
		parameters.put("searchField", searchField);
		parameters.put("geocoded", counter);
		
		parameters.put("outfile", this.outFile.getAbsolutePath());
		
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

	public void setRefs(Set<String> refs) {
		this.refs = refs;
	}

}
