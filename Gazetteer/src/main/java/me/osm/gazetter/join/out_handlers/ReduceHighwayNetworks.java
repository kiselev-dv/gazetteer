package me.osm.gazetter.join.out_handlers;

import me.osm.gazetter.striper.FeatureTypes;
import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.striper.JSONFeature;

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.code.externalsorting.Reducer;

/**
 * Merge highway networks (merge members array)
 * */
public final class ReduceHighwayNetworks implements Reducer {
	
	public static final Reducer INSTANCE = new ReduceHighwayNetworks();

	@Override
	public String merge(String lastLine, String r) {
		
		String ftype1 = GeoJsonWriter.getFtype(lastLine);
		String ftype2 = GeoJsonWriter.getFtype(r);
		
		if(FeatureTypes.HIGHWAY_NET_FEATURE_TYPE.equals(ftype1) && 
				FeatureTypes.HIGHWAY_NET_FEATURE_TYPE.equals(ftype2)) {
			
			JSONObject obj1 = new JSONFeature(lastLine);
			JSONObject obj2 = new JSONFeature(r);
			
			JSONArray array = obj1.getJSONArray("members");
			for(int i = 0; i < obj2.getJSONArray("members").length(); i++) {
				array.put(obj2.getJSONArray("members").get(i));
			}
			
			return obj1.toString();
		}
		
		return lastLine;
	}
}