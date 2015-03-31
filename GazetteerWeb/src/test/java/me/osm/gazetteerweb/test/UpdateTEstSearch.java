package me.osm.gazetteerweb.test;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public class UpdateTEstSearch {
	
	public static void main(String[] args) {
		try {
			JSONObject report = new JSONObject(IOUtils.toString(UpdateTEstSearch.class
					.getResourceAsStream("/checkuik.report.json")));
			
			Set<String> skip = new HashSet<>();
			
			JSONArray reports = report.getJSONArray("reports");
			for(int i = 0; i < reports.length(); i++) {
				JSONObject rep = reports.getJSONObject(i);
				if(!"OK".equals(rep.getString("action"))) {
					skip.add(rep.getString("name"));
				}
			}
			
			JSONObject test = new JSONObject(IOUtils.toString(UpdateTEstSearch.class
					.getResourceAsStream("/test_uik.json")));
			
			Set<String> filter = new HashSet<>();
			List<JSONObject> filtered = new ArrayList<JSONObject>();
			
			JSONArray cases = test.getJSONArray("cases");
			for(int i = 0; i < cases.length(); i++) {
				JSONObject caze = cases.getJSONObject(i);
				if(filter.add(caze.getString("name"))) {
					
					filtered.add(caze);
					
					if(skip.contains(caze.getString("name"))) {
						caze.put("skip", true);
					}
				} 
			}
			
			test.put("cases", new JSONArray(filtered));
			FileOutputStream output = new FileOutputStream("filtered.json");
			IOUtils.write(test.toString(4), output);
			
			output.flush();
			output.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
}
