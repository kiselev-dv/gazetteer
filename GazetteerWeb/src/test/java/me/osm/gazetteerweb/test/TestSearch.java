package me.osm.gazetteerweb.test;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.osm.gazetteer.web.Main;

import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestSearch {

	private static final String SEARCH_LOCATION = "http://localhost:8080/api/location/_search";
	private static final Logger log = LoggerFactory.getLogger(TestSearch.class.getName()); 
	
	public static void main(String[] args) {
		try {
			
			if(TestSearchUtils.available(8080)) {
				Main.main(args);
			}

			TestSearch me = new TestSearch();

			//me.doTest("/test_cityes.json");
			//me.doTest("/test_addresses.json");
			me.doTest("/test_uik.json");

		} catch (Exception e) {
			e.printStackTrace();
		}
		
		System.exit(0);
	}

	private void doTest(String resourceName) {
		JSONObject task = readJSON(resourceName);
		
		log.info("Run {}", task.optString("name"));
		
		boolean success = true;

		List<String> fails = new ArrayList<String>();
		
		JSONArray cases = task.optJSONArray("cases");
		for (int i = 0; i < cases.length(); i++) {
			JSONObject caze = cases.getJSONObject(i);
			boolean caseSuccess = doCase(caze, i, cases.length());
			if(!caseSuccess) {
				fails.add(caze.optString("name"));
			}
			success = caseSuccess && success;
		}
		
		if (success) {
			log.trace("DONE {}", task.optString("name"));
		}
		else {
			if(fails.size() < 10) {
				log.warn("FAILS: {}", fails);
			}
			else {
				log.warn("{} from {} are failed", fails.size(), cases.length());
			}
			log.warn("FAILED {}", task.optString("name"));
		}
	}

	private boolean doCase(JSONObject caze, int i, int total) {
		
		if(caze.optBoolean("skip")) {
			log.info("Skip {} {}", caze.optString("name"), caze.optString("comment"));
			return true;
		}
		
		log.info("Run {} from {}. Case {}", new Object[]{i + 1, total, caze.optString("name")});

		JSONObject jsonObject = caze.getJSONObject("request");

		JSONObject answer = getRequestResult(jsonObject);
		
		if(answer == null) {
			log.error("failed to get answer");
		}
		
		boolean success = checkAnswer(caze, answer);
		
		if(success) {
			log.trace("\tCase {} OK", caze.optString("name")); 
			return true;
		}
		else {
			log.warn("\tCase {} FAILED", caze.optString("name"));
			return false;
		}

	}

	private boolean checkAnswer(JSONObject caze, JSONObject answer) {
		
		if(caze.has("first_result")) {
			
			JSONObject fr = getFirstResult(answer);
			if(fr == null) {
				log.warn("\tFAILED Empty answer");
				return false;
			}
				
			JSONObject check = caze.getJSONObject("first_result");
			
			if(false == firstResultCheck(fr, check)) {
				return false;
			}
		}

		if(caze.has("first_page")) {
			
			for(int i = 0; i < 20; i++) {
				JSONObject fr = getNthResult(answer, i);

				if(fr == null) {
					return false;
				}
				
				JSONObject check = caze.getJSONObject("first_page");
				
				if(firstResultCheck(fr, check)) {
					return true;
				}
			}
			
			return false;
		}

		return true;
	}

	private boolean firstResultCheck(JSONObject obj, JSONObject check) {
		
		if(check.has("location")) {
			if(false == checkLocation(obj, check)) {
				return false;
			}
		}

		if(check.has("type")) {
			if(false == checkType(obj, check)) {
				return false;
			}
		}
		
		return true;
	}

	private boolean checkType(JSONObject obj, JSONObject check) {
		Object type = check.get("type");
		
		Set<String> types = new HashSet<String>();
		
		if(type instanceof String) {
			types.add((String) type);
		}
		else if(type instanceof JSONArray) {
			for(int i = 0; i < ((JSONArray)type).length(); i++) {
				types.add(((JSONArray)type).getString(i));
			}
		}
		
		String objType = obj.optString("type");
		
		if(types.contains(objType)) {
			log.trace("\tType OK");
			return true;
		}
		else {
			log.warn("\tType FAILED Unexpected type {}", objType);
			return false;
		}
		
	}

	private boolean checkLocation(JSONObject obj, JSONObject check) {
		
		String mapKey = check.getString("location");

		if(mapKey != null) {
			String[] split = StringUtils.split(mapKey, "/");
			double delta = Double.valueOf(split[0]);
			Double lat = Double.valueOf(split[1]);
			Double lon = Double.valueOf(split[2]);
			
			JSONObject cp = obj.optJSONObject("center_point");
			double aLon = cp.getDouble("lon");
			double aLat = cp.getDouble("lat");
			
			if(Math.abs(lat - aLat) > delta || Math.abs(lon - aLon) > delta) {
				log.warn("\tLocation: FAILED");
				return false;
			}
			
		}
		
		log.trace("\tLocation: OK");
		return true;
	}

	private JSONObject getFirstResult(JSONObject answer) {
		if(answer == null) {
			return null;
		}
		
		JSONArray features = answer.optJSONArray("features");
		
		if(features == null) {
			log.error("Answer doesnt have features");
			return null;
		}

		return features.optJSONObject(0);
	}

	private JSONObject getNthResult(JSONObject answer, int index) {
		if(answer == null) {
			return null;
		}
		
		JSONArray features = answer.optJSONArray("features");
		
		if(features == null) {
			log.error("Answer doesnt have features");
			return null;
		}
		
		if(features.length() > index) {
			return features.optJSONObject(index);
		}

		return null;
	}

	private JSONObject getRequestResult(JSONObject jsonObject) {

		HttpMethod method = new GetMethod(SEARCH_LOCATION);

		NameValuePair[] opts = new NameValuePair[jsonObject.length() + 1];
		int index = 0;
		for(String key : (Set<String>)jsonObject.keySet()) {
			opts[index++] = new NameValuePair(key, jsonObject.getString(key));
		}
		opts[index] = new NameValuePair("explain", "true");
		
		method.setQueryString(opts);
		
		return get(method);
	}

	private JSONObject readJSON(String resource) {
		JSONObject settings;
		try {
			settings = new JSONObject(IOUtils.toString(getClass()
					.getResourceAsStream(resource)));
		} catch (IOException e) {
			throw new RuntimeException("couldn't read index settings", e);
		}
		return settings;
	}

	private JSONObject get(HttpMethod method) {
		try {
			URL url = new URL(method.getURI().getEscapedURI());
			byte[] byteArray = IOUtils.toByteArray(url.openStream());
			return new JSONObject(new String(byteArray, "UTF8"));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

}