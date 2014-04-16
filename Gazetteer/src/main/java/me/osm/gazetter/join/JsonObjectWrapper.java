package me.osm.gazetter.join;

import org.json.JSONObject;

public class JsonObjectWrapper {
	
	private JSONObject o;
	
	public JsonObjectWrapper(JSONObject o) {
		this.o = o;
	}
	
	@Override
	public int hashCode() {
		return o.getString("id").hashCode();
	}
	
	public JSONObject getObject() {
		return o;
	}
	
	@Override
	public boolean equals(Object obj) {
		
		return obj != null && hashCode() == obj.hashCode();
	}

	public String getId() {
		return o.getString("id");
	}
}