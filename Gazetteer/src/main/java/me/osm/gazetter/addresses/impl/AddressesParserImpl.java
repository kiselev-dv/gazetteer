package me.osm.gazetter.addresses.impl;

import static me.osm.gazetter.addresses.AddressesLevelsMatcher.ADDR_LVL;
import static me.osm.gazetter.addresses.AddressesLevelsMatcher.ADDR_LVL_SIZE;
import static me.osm.gazetter.addresses.AddressesLevelsMatcher.ADDR_NAME;
import static me.osm.gazetter.addresses.AddressesLevelsMatcher.ADDR_NAMES;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.osm.gazetter.Options;
import me.osm.gazetter.addresses.AddrLevelsComparator;
import me.osm.gazetter.addresses.AddrLevelsSorting;
import me.osm.gazetter.addresses.AddrTextFormatter;
import me.osm.gazetter.addresses.AddressesLevelsMatcher;
import me.osm.gazetter.addresses.AddressesParser;
import me.osm.gazetter.addresses.AddressesSchemesParser;
import me.osm.gazetter.addresses.AddressesUtils;
import me.osm.gazetter.addresses.sorters.CityStreetHNComparator;
import me.osm.gazetter.addresses.sorters.HNStreetCityComparator;
import me.osm.gazetter.addresses.sorters.StreetHNCityComparator;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public class AddressesParserImpl implements AddressesParser {
	
	//DI meaning dependencies
	private AddressesSchemesParser schemesParser;
	private AddressesLevelsMatcher levelsMatcher;
	private AddrTextFormatter textFormatter;

	public AddressesParserImpl(AddressesSchemesParser schemesParser, 
			AddressesLevelsMatcher levelsMatcher, AddrTextFormatter textFormatter) {
		this.schemesParser = schemesParser; 
		this.levelsMatcher = levelsMatcher; 
		this.textFormatter = textFormatter; 
	}
	
	public AddressesParserImpl() {
		schemesParser = new AddressesSchemesParserImpl();
		levelsMatcher = new AddressesLevelsMatcherImpl(ADDR_LVL_COMPARATOR, new NamesMatcherImpl());
		textFormatter = new AddrTextFormatterImpl();
	}
	
	private static final AddrLevelsComparator ADDR_LVL_COMPARATOR;
	static {
		AddrLevelsSorting sorting = Options.get().getSorting();
		if(AddrLevelsSorting.HN_STREET_CITY == sorting) {
			ADDR_LVL_COMPARATOR = new HNStreetCityComparator();
		}
		else if (AddrLevelsSorting.CITY_STREET_HN == sorting) {
			ADDR_LVL_COMPARATOR = new CityStreetHNComparator();
		}
		else {
			ADDR_LVL_COMPARATOR = new StreetHNCityComparator();
		}
	}

	private static final String ADDR_PARTS = "parts";

	private static final String ADDR_TEXT = "text";

	private static final String ADDR_FULL = "addr:full";

	
	@Override
	public JSONArray parse(JSONObject addrPoint, List<JSONObject> boundaries, 
			List<JSONObject> nearbyStreets, JSONObject nearestPlace, 
			JSONObject nearestNeighbour, JSONObject associatedStreet) {
		
		Map<String, JSONObject> level2Boundary = new HashMap<String, JSONObject>();
		for(JSONObject b : boundaries) {
			String addrLevel = getAddrLevel(b);
			if(addrLevel != null) {
				level2Boundary.put(addrLevel, b);
			}
		}
		
		JSONArray result = new JSONArray();
		
		JSONObject properties = addrPoint.getJSONObject("properties");
		List<JSONObject> addresses = schemesParser.parseSchemes(properties);
		
		for(JSONObject addrRow : addresses) {
			List<JSONObject> addrJsonRow = new ArrayList<>();
			Set<String> matchedBoundaries = new HashSet<>();
			
			JSONObject postCodeJSON = levelsMatcher.postCodeAsJSON(addrPoint, addrRow);
			if(postCodeJSON != null) {
				addrJsonRow.add(postCodeJSON);
			}

			JSONObject letterAsJSON = levelsMatcher.letterAsJSON(addrPoint, addrRow);
			if(letterAsJSON != null) {
				addrJsonRow.add(letterAsJSON);
			}

			addrJsonRow.add(levelsMatcher.hnAsJSON(addrPoint, addrRow));
			
			JSONObject streetAsJSON = levelsMatcher.streetAsJSON(addrPoint, addrRow, associatedStreet, nearbyStreets);
			if(streetAsJSON != null) {
				addrJsonRow.add(streetAsJSON);
			}
			
			JSONObject quarterJSON = levelsMatcher.quarterAsJSON(addrPoint, addrRow, level2Boundary, nearestNeighbour);
			if(quarterJSON != null) {
				addrJsonRow.add(quarterJSON);
				String bndryLNK = quarterJSON.optString("lnk");
				if(!StringUtils.isEmpty(bndryLNK)) {
					matchedBoundaries.add(bndryLNK);
				}
			}

			JSONObject cityJSON = levelsMatcher.cityAsJSON(addrPoint, addrRow, level2Boundary, nearestPlace);
			if(cityJSON != null) {
				addrJsonRow.add(cityJSON);
				String bndryLNK = cityJSON.optString("lnk");
				if(!StringUtils.isEmpty(bndryLNK)) {
					matchedBoundaries.add(bndryLNK);
				}
			}
			
			for(JSONObject bndry : boundaries) {
				String addrLevel = getAddrLevel(bndry); 
				
				if(addrLevel != null) {
					
					Map<String, String> nTags = AddressesUtils.filterNameTags(bndry);
					
					//skip unnamed
					if(!nTags.containsKey(AddressesLevelsMatcher.ADDR_NAME)) {
						continue;
					}

					//already added
					if(matchedBoundaries.contains(bndry.getString("id"))) {
						continue;
					}
					JSONObject addrLVL = new JSONObject();
					addrLVL.put("lnk", bndry.getString("id"));
					
					
					addrLVL.put(ADDR_LVL, addrLevel);
					addrLVL.put(ADDR_LVL_SIZE, ADDR_LVL_COMPARATOR.getLVLSize(addrLevel));
					addrLVL.put(ADDR_NAME, nTags.get(ADDR_NAME));
					addrLVL.put(ADDR_NAMES, new JSONObject(nTags));
					
					addrJsonRow.add(addrLVL);
				}
			}
			
			Collections.sort(addrJsonRow, ADDR_LVL_COMPARATOR);
			
			JSONObject fullAddressRow = new JSONObject();
			
			fullAddressRow.put(ADDR_TEXT, textFormatter.joinNames(addrJsonRow, properties));
			
			fullAddressRow.put(ADDR_PARTS, new JSONArray(addrJsonRow));
			fullAddressRow.put(AddressesSchemesParser.ADDR_SCHEME, 
					addrRow.optString(AddressesSchemesParser.ADDR_SCHEME));
			
			result.put(fullAddressRow);
		}
		
		if(StringUtils.isNotBlank(properties.optString(ADDR_FULL))) {
			JSONObject addrFull = new JSONObject();
			addrFull.put(ADDR_TEXT, properties.optString(ADDR_FULL));
			addrFull.put(AddressesSchemesParser.ADDR_SCHEME, properties.optString(ADDR_FULL));
			result.put(addrFull);
		}
		
		return result;
	}

	private static String getAddrLevel(JSONObject obj) {
		
		JSONObject properties = obj.optJSONObject("properties");
		
		if(properties == null) {
			properties = obj;
		}
		
		String pk = "place:" + properties.optString("place");
		if(ADDR_LVL_COMPARATOR.supports(pk)) {
			return pk;
		}

		String bk = "boundary:" + properties.optString("admin_level").trim();
		if(ADDR_LVL_COMPARATOR.supports(bk)) {
			return bk;
		}
		
		return null;
	}

	@Override
	public JSONObject boundariesAsArray(List<JSONObject> input) {
		List<JSONObject> result = new ArrayList<>();
		
		for(JSONObject bndry : input) {
			String addrLevel = getAddrLevel(bndry); 
			if(addrLevel != null) {
				
				JSONObject addrLVL = new JSONObject();
				addrLVL.put("lnk", bndry.getString("id"));
				
				Map<String, String> nTags = AddressesUtils.filterNameTags(bndry);
				
				if(!nTags.containsKey(ADDR_NAME)) {
					continue;
				}
				
				addrLVL.put(ADDR_LVL, addrLevel);
				addrLVL.put(ADDR_LVL_SIZE, ADDR_LVL_COMPARATOR.getLVLSize(addrLevel));
				addrLVL.put(ADDR_NAME, nTags.get(ADDR_NAME));
				addrLVL.put(ADDR_NAMES, new JSONObject(nTags));
				
				result.add(addrLVL);
			}
		}

		JSONObject fullAddressRow = new JSONObject();
		Collections.sort(result, ADDR_LVL_COMPARATOR);
		
		fullAddressRow.put(ADDR_TEXT,  textFormatter.joinBoundariesNames(result));
		fullAddressRow.put(ADDR_PARTS, new JSONArray(result));
		
		return fullAddressRow;
	} 
	
}
