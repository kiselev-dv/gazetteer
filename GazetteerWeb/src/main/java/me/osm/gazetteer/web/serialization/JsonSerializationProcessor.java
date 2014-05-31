package me.osm.gazetteer.web.serialization;

import org.json.JSONObject;
import org.restexpress.serialization.json.JacksonJsonProcessor;

import com.fasterxml.jackson.databind.module.SimpleModule;

public class JsonSerializationProcessor extends JacksonJsonProcessor {
	
	@Override
	protected void initializeModule(SimpleModule module) {
		super.initializeModule(module);
		module.addSerializer(JSONObject.class, new JSONObjectJsonSerializer());
	}
}
