package me.osm.gazetter.addresses.impl;

import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;

import java.util.List;
import java.util.Map;

import me.osm.gazetter.addresses.AddrLevelsComparator;
import me.osm.gazetter.addresses.AddressesLevelsMatcher;
import me.osm.gazetter.addresses.AddressesUtils;
import me.osm.gazetter.matchers.NamesMatcher;
import me.osm.gazetter.striper.GeoJsonWriter;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AddressesLevelsMatcherImpl implements AddressesLevelsMatcher {
	
	private static final Logger log = LoggerFactory.getLogger(AddressesLevelsMatcherImpl.class);
	
	public AddressesLevelsMatcherImpl(AddrLevelsComparator lelvelsComparator, 
			NamesMatcher namesMatcher) {
		
		this.lelvelsComparator = lelvelsComparator;
		this.namesMatcher = namesMatcher;
	
	}
	
	private AddrLevelsComparator lelvelsComparator;
	private NamesMatcher namesMatcher;
	
	@Override
	public JSONObject letterAsJSON(JSONObject addrPoint, JSONObject addrRow) {
		
		String letter = addrRow.optString("addr:letter");
		
		if(StringUtils.isEmpty(letter)) {
			return null;
		}
		
		JSONObject letterAddrPart = new JSONObject();
		
		letterAddrPart.put(ADDR_NAME, letter);
		letterAddrPart.put(ADDR_LVL, "letter");
		letterAddrPart.put(ADDR_LVL_SIZE, lelvelsComparator.getLVLSize("street"));
		letterAddrPart.put("lnk", addrPoint.optString("id"));

		return letterAddrPart;
	}
	
	@Override
	public JSONObject hnAsJSON(JSONObject addrPoint, JSONObject addrRow) {
		JSONObject hnAddrPart = new JSONObject();
		
		String hn = addrRow.optString("addr:housenumber");
		hnAddrPart.put(ADDR_NAME, hn);
		hnAddrPart.put(ADDR_LVL, "hn");
		hnAddrPart.put(ADDR_LVL_SIZE, lelvelsComparator.getLVLSize("hn"));
		hnAddrPart.put("lnk", addrPoint.optString("id"));

		JSONObject names = new JSONObject();
		hnAddrPart.put(ADDR_NAMES, names);
		
		if(addrRow.has("addr:housename")) {
			names.put("addr:housename", addrRow.getString("addr:housename"));
		}

		if(addrRow.has("addr:hn-orig")) {
			names.put("addr:hn-orig", addrRow.getString("addr:hn-orig"));
		}
		
		return hnAddrPart;
	}

	@Override
	public JSONObject streetAsJSON(JSONObject addrPoint, JSONObject addrRow,
			JSONObject associatedStreet, List<JSONObject> nearbyStreets) {
			
		if(associatedStreet != null) {
			TLongSet waysSet = new TLongHashSet();
			JSONArray jsonArray = associatedStreet.getJSONArray("associatedWays");
			for(int i = 0; i < jsonArray.length(); i++) {
				waysSet.add(jsonArray.getLong(i));
			}
			
			for(JSONObject ls : nearbyStreets) {
				long wayId = Long.parseLong(StringUtils.split(ls.getString("id"), '-')[2].substring(1));  
				if(waysSet.contains(wayId)) {
					
					JSONObject streetAddrPart = new JSONObject();
					
					Map<String, String> nameTags = AddressesUtils.filterNameTags(ls);
					nameTags.putAll(AddressesUtils.filterNameTags(associatedStreet));
					
					if(nameTags.get("name") != null) {
						streetAddrPart.put(ADDR_NAME, nameTags.get("name"));
						streetAddrPart.put(ADDR_LVL, "street");
						streetAddrPart.put(ADDR_NAMES, new JSONObject(nameTags));
						streetAddrPart.put("lnk", ls.optString("id"));
						streetAddrPart.put(ADDR_LVL_SIZE, lelvelsComparator.getLVLSize("street"));
						
						return streetAddrPart;
					}
					else {
						log.warn("Can't find name for associated street.\nStreet:\n{}\nRelation\n{}", 
								ls.toString(), associatedStreet.toString());
					}
				}
			}
		}
		
		if(!addrRow.has("addr:street")) {
			return null;
		}
		
		JSONObject matchedStreet = null;
		
		String street = addrRow.getString("addr:street");

		if(nearbyStreets != null) {
			for(JSONObject ls : nearbyStreets) {
				if(namesMatcher.isStreetNameMatch(street, AddressesUtils.filterNameTags(ls))) {
					matchedStreet = ls;
					break;
				}
			}
		}
		
		JSONObject streetAddrPart = new JSONObject();
		streetAddrPart.put(ADDR_NAME, street);
		streetAddrPart.put(ADDR_LVL, "street");
		streetAddrPart.put(ADDR_LVL_SIZE, lelvelsComparator.getLVLSize("street"));
		
		if(matchedStreet != null) {
			streetAddrPart.put(ADDR_NAMES, new JSONObject(AddressesUtils.filterNameTags(matchedStreet)));
			streetAddrPart.put("lnk", matchedStreet.optString("id"));
		}
		
		return streetAddrPart;
	}

	@Override
	public JSONObject quarterAsJSON(JSONObject addrPoint, JSONObject addrRow,
			Map<String, JSONObject> level2Boundary, JSONObject nearestNeighbour) {
		
		
		if(addrRow.has("addr:quarter")) {
			JSONObject quarterJSON = new JSONObject();
			String name = addrRow.getString("addr:quarter");
			quarterJSON.put(ADDR_NAME, name);
			quarterJSON.put(ADDR_LVL, "place:quarter");
			quarterJSON.put(ADDR_LVL_SIZE, lelvelsComparator.getLVLSize("place:quarter"));
			
			JSONObject obj = null;
			
			if(obj == null) {
				obj = level2Boundary.get("place:quarter");
			}
			
			if(obj == null || !namesMatcher.isPlaceNameMatch(name, AddressesUtils.filterNameTags(obj))) {
				obj = level2Boundary.get("place:neighbour");
			}
			
			if(obj != null) {
				quarterJSON.put("lnk", obj.getString("id"));
				quarterJSON.put(ADDR_NAMES, AddressesUtils.filterNameTags(obj));
			}
			
			return quarterJSON;
		}
		
		return null;
		
	}
	
	@Override
	public JSONObject cityAsJSON(JSONObject addrPoint, JSONObject addrRow,
			Map<String, JSONObject> level2Boundary, JSONObject nearestPlace) {
		
		if(addrRow.has("addr:city")) {
			JSONObject cityJSON = new JSONObject();
			String name = addrRow.getString("addr:city");
			cityJSON.put(ADDR_NAME, name);
			cityJSON.put(ADDR_LVL, "place:city");
			cityJSON.put(ADDR_LVL_SIZE, lelvelsComparator.getLVLSize("place:city"));
			
			JSONObject obj = null;
			
			if(obj == null) {
				obj = level2Boundary.get("place:hamlet");
			}
			
			if(obj == null || !namesMatcher.isPlaceNameMatch(name, AddressesUtils.filterNameTags(obj))) {
				obj = level2Boundary.get("place:village");
			}

			if(obj == null || !namesMatcher.isPlaceNameMatch(name, AddressesUtils.filterNameTags(obj))) {
				obj = level2Boundary.get("place:town");
			}

			if(obj == null || !namesMatcher.isPlaceNameMatch(name, AddressesUtils.filterNameTags(obj))) {
				obj = level2Boundary.get("place:city");
			}

			if(obj != null) {
				cityJSON.put("lnk", obj.getString("id"));
				cityJSON.put(ADDR_NAMES, AddressesUtils.filterNameTags(obj));
			}
			
			return cityJSON;
		}
		
		return null;
	}

	@Override
	public JSONObject postCodeAsJSON(JSONObject addrPoint, JSONObject addrRow) {
		
		String name = addrRow.optString("addr:postcode");
		if(name != null) {
			JSONObject postCodeJSON = new JSONObject();
			postCodeJSON.put(ADDR_NAME, name);
			postCodeJSON.put(ADDR_LVL, "postcode");
			postCodeJSON.put(ADDR_LVL_SIZE, lelvelsComparator.getLVLSize("postcode"));
			
			return postCodeJSON;
		}
		
		return null;
	}


}
