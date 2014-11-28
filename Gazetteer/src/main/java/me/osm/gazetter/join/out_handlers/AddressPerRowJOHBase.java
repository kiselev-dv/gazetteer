package me.osm.gazetter.join.out_handlers;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public abstract class AddressPerRowJOHBase extends SingleWriterJOHBase {

	protected abstract void handle(JSONObject object, JSONObject address, String stripe);

	@Override
	public void handle(JSONObject object, String stripe) {
		List<JSONObject> addresses = listAddresses(object);
		if(addresses != null && addresses.isEmpty()) {
			for(JSONObject address : addresses) {
				handle(object, address, stripe);
			}
		}
		else {
			handle(object, null, stripe);
		}
	}

	protected List<JSONObject> listAddresses(JSONObject object) {
		
		//String ftype = object.optString("ftype");
		JSONArray addrJsonArray = object.optJSONArray("addresses");
		
		List<JSONObject> result = new ArrayList<JSONObject>();
		if(addrJsonArray != null) {
			for(int i = 0; i < addrJsonArray.length(); i++) {
				JSONObject addr = addrJsonArray.optJSONObject(i);
				if(addr != null) {
					result.add(addr);
				}
			}
		}
		
		return result;
	}

}
