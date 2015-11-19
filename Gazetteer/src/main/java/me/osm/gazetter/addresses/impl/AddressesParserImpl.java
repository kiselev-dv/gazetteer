package me.osm.gazetter.addresses.impl;

import static me.osm.gazetter.addresses.AddressesLevelsMatcher.ADDR_LVL;
import static me.osm.gazetter.addresses.AddressesLevelsMatcher.ADDR_LVL_SIZE;
import static me.osm.gazetter.addresses.AddressesLevelsMatcher.ADDR_NAME;
import static me.osm.gazetter.addresses.AddressesLevelsMatcher.ADDR_NAMES;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import me.osm.gazetter.addresses.AddrLevelsComparator;
import me.osm.gazetter.addresses.AddrLevelsSorting;
import me.osm.gazetter.addresses.AddrTextFormatter;
import me.osm.gazetter.addresses.AddressesLevelsMatcher;
import me.osm.gazetter.addresses.AddressesParser;
import me.osm.gazetter.addresses.AddressesSchemesParser;
import me.osm.gazetter.addresses.AddressesUtils;
import me.osm.gazetter.addresses.Constants;
import me.osm.gazetter.addresses.sorters.CityStreetHNComparator;
import me.osm.gazetter.addresses.sorters.HNStreetCityComparator;
import me.osm.gazetter.addresses.sorters.StreetHNCityComparator;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Default implementation for
 * {@link AddressesParser}
 * */
public class AddressesParserImpl implements AddressesParser {
	
	//DI meaning dependencies
	protected AddressesSchemesParser schemesParser;
	protected AddressesLevelsMatcher levelsMatcher;
	protected AddrTextFormatter textFormatter;
	protected AddrLevelsComparator addrLevelComparator;
	
	protected Set<String> skipInFullText;
	protected List<String> cityBoundaries;
	protected boolean findLangs;

	protected static final Set<String> langCodes = new HashSet<>(Arrays.asList(Locale.getISOLanguages()));
	protected static final Set<String> hashedBoundariesLelvels = new HashSet<String>();
	static {
		hashedBoundariesLelvels.addAll(Arrays.asList(
				"place:hamlet", 
				"place:village", 
				"place:town", 
				"place:city", 
				"boundary:8", 
				"boundary:7", 
				"boundary:6", 
				"boundary:5", 
				"boundary:4", 
				"boundary:3", 
				"boundary:2"
		));
	}
	
	/**
	 * Create parser with parameters
	 * 
	 * @param schemesParser
	 * @param levelsMatcher
	 * @param textFormatter
	 * @param sorting
	 * @param skipInFullText
	 * @param findLangs
	 */
	public AddressesParserImpl(AddressesSchemesParser schemesParser, 
			AddressesLevelsMatcher levelsMatcher, AddrTextFormatter textFormatter,
			AddrLevelsSorting sorting, Set<String> skipInFullText, boolean findLangs) {
		
		this.schemesParser = schemesParser; 
		this.levelsMatcher = levelsMatcher; 
		this.textFormatter = textFormatter;
		
		if(AddrLevelsSorting.HN_STREET_CITY == sorting) {
			addrLevelComparator = new HNStreetCityComparator();
		}
		else if (AddrLevelsSorting.CITY_STREET_HN == sorting) {
			addrLevelComparator = new CityStreetHNComparator();
		}
		else {
			addrLevelComparator = new StreetHNCityComparator();
		}
		
		this.skipInFullText = skipInFullText;

		this.cityBoundaries = 
				Arrays.asList("place:hamlet", "place:village", "place:town", "place:city", "boundary:8");
		
		this.findLangs = findLangs;
	}
	
	
	/**
	 * Default implementation
	 */
	public AddressesParserImpl() {
		
		this.cityBoundaries = 
				Arrays.asList("place:hamlet", "place:village", "place:town", "place:city", "boundary:8");
		
		addrLevelComparator = new HNStreetCityComparator();
		schemesParser = new AddressesSchemesParserImpl();
		levelsMatcher = new AddressesLevelsMatcherImpl(
				addrLevelComparator, 
				new NamesMatcherImpl(),
				this.cityBoundaries);
		textFormatter = new AddrTextFormatterImpl();
		skipInFullText = new HashSet<>();
		this.findLangs = false;
	}
	

	protected static final String ADDR_PARTS = "parts";

	protected static final String ADDR_TEXT = "text";
	protected static final String ADDR_TEXT_LONG = "longText";

	protected static final String ADDR_FULL = "addr:full";

	
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

			addrJsonRow.add(levelsMatcher.hnAsJSON(addrPoint, addrRow));
			
			JSONObject streetAsJSON = levelsMatcher.streetAsJSON(
					addrPoint, addrRow, associatedStreet, nearbyStreets, hashBoundaries(boundaries));
			
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

			JSONObject cityJSON = levelsMatcher.cityAsJSON(addrPoint, addrRow, level2Boundary, 
					nearestPlace, getAddrLevel(nearestPlace));
			
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
					addrLVL.put(ADDR_LVL_SIZE, addrLevelComparator.getLVLSize(addrLevel));
					addrLVL.put(ADDR_NAME, nTags.get(ADDR_NAME));
					addrLVL.put(ADDR_NAMES, new JSONObject(nTags));
					
					addrJsonRow.add(addrLVL);
				}
				
			}
			
			result.put(createAddressRow(properties, addrRow, addrJsonRow));
		}
		
		return result;
	}

	protected JSONObject createAddressRow(JSONObject properties,
			JSONObject addrRow, List<JSONObject> addrJsonRow) {
		
		List<JSONObject> filtered = filterForFullText(addrJsonRow);
		Set<String> langs = getLangs(filtered);
		
		Collections.sort(filtered, addrLevelComparator);
		Collections.sort(addrJsonRow, addrLevelComparator);
		
		JSONObject fullAddressRow = new JSONObject();
		
		fullAddressRow.put(ADDR_TEXT, textFormatter.joinNames(filtered, properties, null));
		fullAddressRow.put(ADDR_TEXT_LONG, textFormatter.joinNames(addrJsonRow, properties, null));

		fullAddressRow.put("langs", langs);
		for(String lang : langs) {
			fullAddressRow.put(ADDR_TEXT + ":" + lang, textFormatter.joinNames(filtered, properties, lang));
		}
		
		fullAddressRow.put(ADDR_PARTS, new JSONArray(addrJsonRow));
		fullAddressRow.put(AddressesSchemesParser.ADDR_SCHEME, 
				addrRow.optString(AddressesSchemesParser.ADDR_SCHEME));

		if(StringUtils.isNotBlank(properties.optString(ADDR_FULL))) {
			fullAddressRow.put(ADDR_FULL, properties.optString(ADDR_FULL));
		}
		
		return fullAddressRow;
	}

	@SuppressWarnings("unchecked")
	protected Set<String> getLangs(List<JSONObject> filtered) {
		if(!this.findLangs) {
			return Collections.emptySet();
		}
		
		Set<String> result = new HashSet<>();
		for(JSONObject lvl : filtered) {
			int lvlSize = lvl.optInt(AddressesLevelsMatcher.ADDR_LVL_SIZE);
			
			if(lvlSize > Constants.HN_LVL_SIZE && lvlSize != Constants.POSTCODE_LVL_SIZE) {
				JSONObject names = lvl.optJSONObject("names");
				
				//streets lvl, init
				if(lvlSize == Constants.STREET_LVL_SIZE) {
					
					//if there is no translation for street - return;
					if(names == null) {
						return Collections.emptySet();
					}
					
					result.addAll(getLangsFromTags(names.keySet()));
				}
				
				if(lvlSize > Constants.STREET_LVL_SIZE) {
					
					if(names == null) {
						return Collections.emptySet();
					}
					
					Set<String> langs = getLangsFromTags(names.keySet());
					
					Iterator<String> it = result.iterator();
					while (it.hasNext()) {
						if(!langs.contains(it.next())) {
							it.remove();
						}
					}
					
					if(result.isEmpty()) {
						return result;
					}
				}
				
			}
		}
		
		return result;
	}

	protected Set<String> getLangsFromTags(Set<String> keySet) {
		Set<String> result = new HashSet<>();
		for(String key : keySet) {
			String[] split = StringUtils.split(key, ':');
			if(split.length > 1) {
				String lang = StringUtils.split(split[1], "-_")[0];
				if(langCodes.contains(lang)) {
					result.add(lang);
				}
			}
		}
		
		return result;
	}

	protected List<JSONObject> filterForFullText(List<JSONObject> addrJsonRow) {
		
		List<JSONObject> list = new ArrayList<>(addrJsonRow);
		Collections.sort(list, new Comparator<JSONObject>() {

			@Override
			public int compare(JSONObject o1, JSONObject o2) {
				int s1 = o1.optInt(AddressesLevelsMatcher.ADDR_LVL_SIZE);
				int s2 = o2.optInt(AddressesLevelsMatcher.ADDR_LVL_SIZE);
				return Integer.compare(s1, s2);
			}
			
		});
		
		JSONObject prevAddrLvl = null;
		Iterator<JSONObject> iterator = list.iterator();
		while(iterator.hasNext()) {
			JSONObject lvl = iterator.next();
			if(lvl.getInt(AddressesLevelsMatcher.ADDR_LVL_SIZE) > 55) {
				boolean skip = skipInFullText.contains(lvl.optString(AddressesLevelsMatcher.ADDR_LVL));
				if(skip || addrLlvlsMatch(prevAddrLvl, lvl)) {
					iterator.remove();
				}
				else {
					prevAddrLvl = lvl;
				}
			}
		}
		
		return list;
	}

	@SuppressWarnings("unchecked")
	protected boolean addrLlvlsMatch(JSONObject prevAddrLvl, JSONObject lvl) {
		if(prevAddrLvl == null || lvl == null) {
			return false;
		}
		String name = prevAddrLvl.optString("name", null);
		String name2 = lvl.optString("name", null);
		if(name != null && name2 != null) {
			if(StringUtils.containsIgnoreCase(name2, name)) {
				return true;
			}
			
			JSONObject optNames = lvl.optJSONObject("names");
			if(optNames != null) {

				for(String key : (Set<String>) optNames.keySet()) {
					String optName = optNames.getString(key);
					
					if(StringUtils.containsIgnoreCase(optName, name)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	@Override
	public String getAddrLevel(JSONObject obj) {
		
		if(obj != null) {
			
			JSONObject properties = obj.optJSONObject("properties");
			
			if(properties == null) {
				properties = obj;
			}
			
			String pk = "place:" + properties.optString("place");
			if(addrLevelComparator.supports(pk)) {
				return pk;
			}
			
			String bk = "boundary:" + properties.optString("admin_level").trim();
			if(addrLevelComparator.supports(bk)) {
				return bk;
			}
			
			if(properties.has("highway")) {
				return "street";
			}
		}
		
		return null;
	}

	@Override
	public JSONObject boundariesAsArray(JSONObject subj, List<JSONObject> input) {
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
				addrLVL.put(ADDR_LVL_SIZE, addrLevelComparator.getLVLSize(addrLevel));
				addrLVL.put(ADDR_NAME, nTags.get(ADDR_NAME));
				addrLVL.put(ADDR_NAMES, new JSONObject(nTags));
				
				result.add(addrLVL);
			}
		}
		
		{
			String addrLevel = getAddrLevel(subj); 
			
			if(addrLevel != null) {
				JSONObject addrLVL = new JSONObject();
				addrLVL.put("lnk", subj.getString("id"));
				
				Map<String, String> nTags = AddressesUtils.filterNameTags(subj);
				
				if(nTags.containsKey(ADDR_NAME)) {
					addrLVL.put(ADDR_LVL, addrLevel);
					addrLVL.put(ADDR_LVL_SIZE, addrLevelComparator.getLVLSize(addrLevel));
					addrLVL.put(ADDR_NAME, nTags.get(ADDR_NAME));
					addrLVL.put(ADDR_NAMES, new JSONObject(nTags));
					
					result.add(addrLVL);
				}
			}
		}
		JSONObject fullAddressRow = createBoundaryAddrRow(result, subj);
		
		fullAddressRow.put("boundariesHash", hashBoundaries(input));
		
		return fullAddressRow;
	}
	
	/**
	 * Calculate unique number for boundaries
	 * 
	 * @param input boundaries
	 * @return hash
	 * */
	public int hashBoundaries(List<JSONObject> input) {
		
		if(input != null) {
		
			StringBuilder hashString = new StringBuilder();
			
			for(JSONObject bndry : input) {
				String addrLevel = getAddrLevel(bndry); 
				if(addrLevel != null && hashedBoundariesLelvels.contains(addrLevel)) {
					hashString.append(bndry.getString("id"));
				}
			}

			if(hashString.length() > 0) {
				return hashString.toString().hashCode();
			}
		}
		
		return 0;
	}

	protected JSONObject createBoundaryAddrRow(List<JSONObject> result, JSONObject subj) {
		List<JSONObject> filtered = filterForFullText(result);

		JSONObject fullAddressRow = new JSONObject();
		Collections.sort(result, addrLevelComparator);
		
		Set<String> langs = getLangs(filtered);
		
		Collections.sort(filtered, addrLevelComparator);
		
		fullAddressRow.put(ADDR_TEXT,  textFormatter.joinBoundariesNames(filtered, null));
		fullAddressRow.put(ADDR_TEXT_LONG,  textFormatter.joinBoundariesNames(result, null));
		fullAddressRow.put(ADDR_PARTS, new JSONArray(result));
		
		fullAddressRow.put("langs", langs);
		for(String lang : langs) {
			fullAddressRow.put(ADDR_TEXT + ":" + lang, textFormatter.joinBoundariesNames(filtered, lang));
		}
		return fullAddressRow;
	} 
	
}
