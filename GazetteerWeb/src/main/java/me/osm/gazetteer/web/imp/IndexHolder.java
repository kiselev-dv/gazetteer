package me.osm.gazetteer.web.imp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import me.osm.gazetteer.web.utils.FileUtils;
import me.osm.gazetteer.web.utils.FileUtils.LineHandler;
import me.osm.gazetteer.web.utils.OSMDocSinglton;
import me.osm.osmdoc.model.Tag.TagValueType;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.client.AdminClient;
import org.elasticsearch.client.Client;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

public class IndexHolder {
	
	public static final String POI_CLASS = "poi_class";
	public static final String LOCATION = "location";
	
	public void createIndex(Client client) {

		AdminClient admin = client.admin();

		JSONObject settings = readJSON("/gazetteer_schema.json");
		settings.getJSONObject("mappings").put(LOCATION, readJSON("/mappings/location.json"));
		
		JSONObject moreTagsProperties = new JSONObject();
		for(Entry<String, String> e : OSMDocSinglton.get().getFacade().listMoreTagsTypes().entrySet()) {
			moreTagsProperties.put(e.getKey(), getPropertyMapping(e.getValue()));
		}
		
		settings.getJSONObject("mappings").getJSONObject(LOCATION)
			.getJSONObject("properties").getJSONObject("more_tags").put("properties", moreTagsProperties);
		
		settings.getJSONObject("mappings").put(POI_CLASS, readJSON("/mappings/poi_class.json"));

		JSONObject indexSettings = settings.getJSONObject("settings");
		addSynonyms(indexSettings);
		
		CreateIndexRequestBuilder request = admin.indices().prepareCreate("gazetteer")
			.setSettings(indexSettings.toString())
			.addMapping(LOCATION, settings.getJSONObject("mappings").getJSONObject(LOCATION).toString())
			.addMapping(POI_CLASS, settings.getJSONObject("mappings").getJSONObject(POI_CLASS).toString());
		
		LoggerFactory.getLogger(getClass()).info("Update mappings");
		
		request.get();
	}
	
	@SuppressWarnings("unchecked")
	public static List<String[]> getCharFilterReplaces() {
		
		List<String[]> res = new ArrayList<>();
		
		JSONObject mapping = readJSON("/gazetteer_schema.json");
		JSONObject charFilter = mapping.getJSONObject("settings").getJSONObject("analysis").optJSONObject("char_filter");
		if(charFilter != null) {
			for(String k : (Set<String>)charFilter.keySet()) {
				JSONObject filter = charFilter.getJSONObject(k);
				if("mapping".equals(filter.optString("type"))){
					JSONArray mappings = filter.getJSONArray("mappings");
					for(int i = 0; i < mappings.length(); i++ ) {
						String fDef = mappings.getString(i);
						String[] split = StringUtils.splitByWholeSeparator(fDef, "=>");
						if(split.length == 2) {
							res.add(split);
						}
					}
				}
			}
		}
		
		return res;
	}

	private JSONObject getPropertyMapping(String value) {
		JSONObject result = new JSONObject();
		
		switch (TagValueType.valueOf(value)) {
		
		case BOOLEAN: result.put("type", "boolean"); break;
		
		case DATE: result.put("type", "date"); break;
		
		case NUMBER: result.put("type", "double"); break;
		
		case OPEN_HOURS: 
			result.put("type", "object");
			result.put("index", "not_analyzed");
			break;
		
		case URL: 
			result.put("type", "string");
			result.put("index", "not_analyzed");
			break;

		case PHONE: 
			result.put("type", "string");
			result.put("index", "not_analyzed");
			break;

		case WIKI: 
			result.put("type", "string");
			result.put("index", "not_analyzed");
			break;
			
		case ENUM: 
			result.put("type", "string");
			result.put("index", "not_analyzed");
			break;
			
		default: 
			result.put("type", "string");
			break;
		
		}
		return result;
	}

	private static JSONObject readJSON(String resource) {
		JSONObject settings;
		try {
			settings = new JSONObject(IOUtils.toString(IndexHolder.class.getResourceAsStream(
					resource)));
		} catch (IOException e) {
			throw new RuntimeException("couldn't read index settings", e);
		}
		return settings;
	}

	private void addSynonyms(JSONObject indexSettings) {
		final JSONArray synonims = fillFormResources();
		
		File confSynonims = new File("config/synonims/");

		if(confSynonims.exists()) {
			for(File f : confSynonims.listFiles()) {
				if(f.getName().endsWith(".syn") && f.isFile()) {
					fillFormFile(f, synonims);
				}
			}
		}
		
		indexSettings.getJSONObject("analysis").getJSONObject("filter")
			.getJSONObject("synonym_filter").put("synonyms", synonims);
		
	}

	private void fillFormFile(File f, final JSONArray synonims) {
		FileUtils.handleLines(f, new LineHandler() {
					
			@Override
			public void handle(String s) {
				if(!s.startsWith("#")) {
					synonims.put(s);
				}
			}
					
		});
	}

	private JSONArray fillFormResources() {
		final JSONArray synonims = new JSONArray();
		
		FileUtils.handleLines(getClass().getResourceAsStream("/synonims"), 
		new LineHandler() {
			
			@Override
			public void handle(String s) {
				if(!s.startsWith("#")) {
					synonims.put(s);
				}
			}
			
		});
		
		return synonims;
	}
}
