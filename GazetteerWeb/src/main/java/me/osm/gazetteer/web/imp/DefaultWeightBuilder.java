package me.osm.gazetteer.web.imp;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public class DefaultWeightBuilder implements ObjectsWeightBuilder {

	private static final Map<String, Integer> baseLevels = new HashMap<String, Integer>();
	static {
		baseLevels.put("poipnt", 1 * 100);
		baseLevels.put("adrpnt", 2 * 100);
		baseLevels.put("neighbour", 3 * 100);
		baseLevels.put("hghway", 4 * 100);
		baseLevels.put("city", 5 * 100 + 50);
		baseLevels.put("place:town", 5 * 100 + 40);
		baseLevels.put("place:village", 5 * 100 + 30);
		baseLevels.put("place:hamlet", 5 * 100 + 20);
		
		//towns inside city like Khimki inside Moscow
		baseLevels.put("place:district", 5 * 100);
		
		baseLevels.put("district", 6 * 100);
		baseLevels.put("state", 7 * 100);
		baseLevels.put("country", 8 * 100);
	}
	
	@Override
	public int weight(JSONObject obj) {
		
		String type = obj.optString("type");
		
		JSONObject address = obj.optJSONObject("address");
		
		Map<String, JSONObject> levels = mapLevels(address);
		
		String baseType = getBaseType(type, obj, levels, address);
		
		Integer baseScore = baseLevels.get(baseType);
		
		obj.put("weight_base_type", baseType);
		
		if(baseScore != null) {
			return baseScore;
		}
		
		return 0;
	}

	private String getBaseType(String type, JSONObject subj, Map<String, JSONObject> levels, JSONObject address) {
		
		switch(type) {

		case "poipnt": return type;
		
		case "adrpnt": return type;

		case "hghway": return type;

		case "nbhdln": return "neighbour";
		
		}
		
		//admbnd plcpnt plcdln 
		
		int alvl = -1;
		String place = null;

		JSONObject tags = subj.optJSONObject("tags");
		if(tags != null) {
			
			Object adminLevel = tags.opt("admin_level");
			
			if(adminLevel != null) {
				if(adminLevel instanceof Integer) {
					alvl = (Integer) adminLevel;
				}
				
				if(adminLevel instanceof String) {
					alvl = Integer.valueOf((String) adminLevel);
				}
			}
				
			place = StringUtils.stripToNull(tags.optString("place"));
		}
		
		if(alvl > 8) {
			return "neighbour";
		}
		
		if("neighbourhood".equals(place) || "quarter".equals(place)) {
			return "neighbour";
		}
		
		//остались города и деревни (place), но если они внутри более крупного города
		//то классифицируем их как район.
		//place: hamlet village town 
		
		if("town".equals(place)) {
			
			if(levels.containsKey("boundary:8") || levels.containsKey("place:city")) {
				return "place:district";
			}

			return "place:town";
		}

		if("village".equals(place)) {
			
			if(levels.containsKey("boundary:8") || levels.containsKey("place:city") || levels.containsKey("place:town")) {
				return "place:district";
			}
			
			return "place:village";
		}

		if("hamlet".equals(place)) {
			
			if(levels.containsKey("boundary:8") || levels.containsKey("place:city") 
					|| levels.containsKey("place:town")) {
				return "place:district";
			}
			
			return "place:hamlet";
		}
		
		if(alvl == 8) {
			return "city";
		}
		
		if(alvl == 6 || alvl == 7) {
			return "district";
		}

		if(alvl == 4 || alvl == 5) {
			return "state";
		}

		if(alvl == 2) {
			return "country";
		}
		
		if("city".equals(place)) {
			return "city";
		}

		
		return null;
	}

	private Map<String, JSONObject> mapLevels(JSONObject address) {
		Map<String, JSONObject> levels = new HashMap<String, JSONObject>();
		if(address != null) {
			JSONArray parts = address.getJSONArray("parts");
			for(int i = 0; i < parts.length(); i++) {
				JSONObject part = parts.getJSONObject(i);
				String lvl = part.optString("lvl");
				if(lvl != null) {
					levels.put(lvl, part);
				}
			}
		}
		
		return levels;
	}

}
