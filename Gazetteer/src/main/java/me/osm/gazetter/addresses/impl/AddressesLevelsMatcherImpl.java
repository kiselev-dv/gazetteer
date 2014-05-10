package me.osm.gazetter.addresses.impl;

import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;

import java.util.List;
import java.util.Map;

import me.osm.gazetter.addresses.AddrLevelsComparator;
import me.osm.gazetter.addresses.AddressesLevelsMatcher;
import me.osm.gazetter.addresses.AddressesUtils;
import me.osm.gazetter.addresses.NamesMatcher;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AddressesLevelsMatcherImpl implements AddressesLevelsMatcher {
	
	private static final Logger log = LoggerFactory.getLogger(AddressesLevelsMatcherImpl.class);
	
	public AddressesLevelsMatcherImpl(AddrLevelsComparator lelvelsComparator, 
			NamesMatcher namesMatcher, List<String> placeBoundaries) {
		
		this.lelvelsComparator = lelvelsComparator;
		this.namesMatcher = namesMatcher;
		
		FORSE_ADDR_CITY_MATCH = false;
		this.placeBoundaries = placeBoundaries;
	}
	
	protected AddrLevelsComparator lelvelsComparator;
	protected NamesMatcher namesMatcher;
	protected boolean FORSE_ADDR_CITY_MATCH;
	protected List<String> placeBoundaries;
	
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
			JSONObject associatedStreet, List<JSONObject> nearbyStreets, int boundariesHash) {
			
		if(associatedStreet != null) {
			TLongSet waysSet = new TLongHashSet();
			JSONArray jsonArray = associatedStreet.getJSONArray("associatedWays");
			for(int i = 0; i < jsonArray.length(); i++) {
				waysSet.add(jsonArray.getLong(i));
			}
			
			if(nearbyStreets != null)
			{
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
							
							streetAddrPart.put("strtUID", getStreetUUID(ls, boundariesHash));
							
							return streetAddrPart;
						}
						else {
							log.warn("Can't find name for associated street.\nStreet:\n{}\nRelation\n{}", 
									ls.toString(), associatedStreet.toString());
						}
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
			streetAddrPart.put("strtUID", getStreetUUID(matchedStreet, boundariesHash));
		}
		
		return streetAddrPart;
	}

	private int getStreetUUID(JSONObject street, int boundariesHash) {
		
		JSONArray streetBoundaries = street.optJSONArray("boundaries");
		
		if(streetBoundaries == null || streetBoundaries.length() == 0) {
			return 0;
		}
		
		for(int i = 0; i < streetBoundaries.length(); i++) {
			int hash = streetBoundaries.getJSONObject(i).optInt("boundariesHash");
			if(boundariesHash == hash) {
				return hash;
			}
		}
		
		return streetBoundaries.getJSONObject(0).optInt("boundariesHash");
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
	
	private static class Cortage {
		public String name;
		public JSONObject obj;
	}
	
	@Override
	public JSONObject cityAsJSON(JSONObject addrPoint, JSONObject addrRow,
			Map<String, JSONObject> level2Boundary, JSONObject nearestPlace, 
			String nearestPlaceLvl) {
		
		String tagCityName = null;
		String name = null;
		String lvl = null;
		
		if(addrRow.has("addr:city")) {
			tagCityName = addrRow.getString("addr:city");
			name = tagCityName; 
			lvl = "place:city";
		}
			
		JSONObject obj = null;
		
		//Search for boundary
		for(String addrKey : placeBoundaries)
		{
			JSONObject hamlet = level2Boundary.get(addrKey);
			Cortage cortage = checkPlace(tagCityName, hamlet);
			if(cortage != null) {
				obj = cortage.obj;
				lvl = addrKey;
				name = cortage.name;
				break;
			}
		}
		
		//Didn't found in boundaries - check nearest
		//but only if we have addr:city tag
		if(obj == null && tagCityName != null && nearestPlace != null) {
			
			Map<String, String> nearestPlaceTags = AddressesUtils.filterNameTags(nearestPlace);
			
			if(lelvelsComparator.supports(nearestPlaceLvl) && 
					namesMatcher.isPlaceNameMatch(tagCityName, nearestPlaceTags)) {
				
				obj = nearestPlace;
				lvl = nearestPlaceLvl;
				name = nearestPlaceTags.get("name");
				if(name == null) {
					name  = tagCityName;
				}
			}
		}
		
		if(obj == null && tagCityName == null) {
			return null;
		}
		
		//at least we have addr:city tag
		JSONObject cityJSON = new JSONObject();
		if(tagCityName != null) {
			cityJSON.put(ADDR_NAME, tagCityName);
			cityJSON.put(ADDR_LVL, "place:city");
			cityJSON.put(ADDR_LVL_SIZE, lelvelsComparator.getLVLSize("place:city"));
		}

		//override min values by obj 
		if(obj != null) {
			cityJSON.put(ADDR_NAME, name);
			cityJSON.put(ADDR_LVL, lvl);
			cityJSON.put("lnk", obj.getString("id"));
			cityJSON.put(ADDR_NAMES, AddressesUtils.filterNameTags(obj));
			cityJSON.put(ADDR_LVL_SIZE, lelvelsComparator.getLVLSize(lvl));
		}
		
		if(cityJSON.optString("name", null) == null) {
			return null;
		}
		
		return cityJSON;
	}

	private Cortage checkPlace(String tagCityName, JSONObject place) {
		
		if(place != null) {
			Map<String, String> hamletNameTags = AddressesUtils.filterNameTags(place);
			
			if(tagCityName != null && FORSE_ADDR_CITY_MATCH) {
				if(namesMatcher.isPlaceNameMatch(tagCityName, hamletNameTags)) {
					Cortage r = new Cortage();
					
					r.obj = place;
					r.name = hamletNameTags.get("name");
					
					if(r.name == null) {
						r.name = tagCityName;
					}
					
					return r;
				}
				
				// with FORSE_ADDR_CITY_MATCH we should keep looking 
				// until matches place will be founded
				
				return null;
			}

			Cortage r = new Cortage();
			r.obj = place;
			r.name = hamletNameTags.get("name");
			
			if(r.name == null) {
				r.name = tagCityName;
			}
			
			return r;
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
