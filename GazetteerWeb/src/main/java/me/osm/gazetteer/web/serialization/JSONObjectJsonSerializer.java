package me.osm.gazetteer.web.serialization;

import java.io.IOException;

import org.json.JSONObject;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

final class JSONObjectJsonSerializer extends
		JsonSerializer<JSONObject> {
	@Override
	public void serialize(JSONObject value, JsonGenerator jgen,
			SerializerProvider provider) throws IOException,
			JsonProcessingException {
		jgen.writeRaw(value.toString());
	}
}