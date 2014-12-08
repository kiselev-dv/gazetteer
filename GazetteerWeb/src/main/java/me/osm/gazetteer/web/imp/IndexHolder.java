package me.osm.gazetteer.web.imp;

import java.io.File;
import java.io.IOException;

import me.osm.gazetteer.web.utils.FileUtils;
import me.osm.gazetteer.web.utils.FileUtils.LineHandler;

import org.apache.commons.io.IOUtils;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.client.AdminClient;
import org.elasticsearch.client.Client;
import org.json.JSONArray;
import org.json.JSONObject;

public class IndexHolder {
	
	public static final String POI_CLASS = "poi_class";
	public static final String LOCATION = "location";
	
	public void createIndex(Client client) {

		AdminClient admin = client.admin();

		JSONObject settings = readJSON("/gazetteer_schema.json");
		settings.getJSONObject("mappings").put(LOCATION, readJSON("/mappings/location.json"));
		settings.getJSONObject("mappings").put(POI_CLASS, readJSON("/mappings/poi_class.json"));

		JSONObject indexSettings = settings.getJSONObject("settings");
		addSynonyms(indexSettings);
		
		CreateIndexRequestBuilder request = admin.indices().prepareCreate("gazetteer")
			.setSettings(indexSettings.toString())
			.addMapping(LOCATION, settings.getJSONObject("mappings").getJSONObject(LOCATION).toString());
		
		request.get();
	}

	private JSONObject readJSON(String resource) {
		JSONObject settings;
		try {
			settings = new JSONObject(IOUtils.toString(getClass().getResourceAsStream(
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
