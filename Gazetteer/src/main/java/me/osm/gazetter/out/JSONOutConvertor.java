package me.osm.gazetter.out;

import me.osm.gazetter.striper.FeatureTypes;

import org.json.JSONObject;

public class JSONOutConvertor implements OutConverter {

	@Override
	public void handle(String s) {
		JSONObject json = new JSONObject(s);
		
		/* TODO: For now handle only addr points,
		 * later rewrite output for particular types
		 * of objects.
		 */
		if(FeatureTypes.ADDR_POINT_FTYPE.equals(json.optString("ftype"))) {
			System.out.println(s);
		} 
	}

	@Override
	public void close() {
		
	}

}
