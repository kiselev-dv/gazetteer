import org.json.JSONObject;

import java.util.List;
import java.util.Set;

import me.osm.gazetter.addresses.AddrLevelsComparator;

import me.osm.gazetter.addresses.AddrLevelsSorting;
import me.osm.gazetter.addresses.AddrTextFormatter;
import me.osm.gazetter.addresses.AddressesParser;
import me.osm.gazetter.addresses.AddressesParserFactory;
import me.osm.gazetter.addresses.AddressesSchemesParser;
import me.osm.gazetter.addresses.NamesMatcher;
import me.osm.gazetter.addresses.impl.AddressesParserImpl;
import me.osm.gazetter.addresses.impl.AddressesLevelsMatcherImpl;
import me.osm.gazetter.addresses.AddressesLevelsMatcher;
import me.osm.gazetter.addresses.AddrLevelsComparator;
import me.osm.gazetter.addresses.sorters.HNStreetCityComparator;
import me.osm.gazetter.addresses.impl.AddressesLevelsMatcherImpl;

import org.apache.commons.lang3.StringUtils;
import org.json.*;

class RuAddressesParserFactory implements AddressesParserFactory {

	public AddressesParser newAddressesParser(
			AddressesSchemesParser addressesSchemesParser,
			AddrLevelsComparator addrLevelComparator,
			NamesMatcher namesMatcherImpl, List<String> cityBoundaryes,
			AddrTextFormatter addrTextFormatter, AddrLevelsSorting sorting,
			Set<String> skippInFullText,
			boolean findLangsLevel) {
		
		
		return new OSMRUAddressesParserImpl(
				addressesSchemesParser, 
				new OSMRUAddressesLevelsMatcher(addrLevelComparator, 
					namesMatcherImpl, 
					["place:hamlet", "place:village", "place:town", "place:city"]),
				addrTextFormatter, 
				sorting,
				skippInFullText,
				findLangsLevel);
	}
} 

class OSMRUAddressesParserImpl extends AddressesParserImpl {
	
	//private AddrLevelsComparator MIN_TO_MAX_ADDR_COMPARATOR = new HNStreetCityComparator();
	
	public OSMRUAddressesParserImpl(AddressesSchemesParser schemesParser, 
			AddressesLevelsMatcher levelsMatcher, AddrTextFormatter textFormatter,
			AddrLevelsSorting sorting, Set<String> skipInFullText, boolean findLangs) {
		
		super(schemesParser, levelsMatcher, textFormatter, sorting, skipInFullText, findLangs);
	}
	
	protected JSONObject createAddressRow(JSONObject properties,
			JSONObject addrRow, List<JSONObject> addrJsonRow) {
		
		List<JSONObject> filtered = filterForFullText(addrJsonRow);
		Set<String> langs = getLangs(filtered);
		
		JSONObject fullAddressRow = new JSONObject();

		//Collections.sort(filtered, MIN_TO_MAX_ADDR_COMPARATOR);
		fullAddressRow.put("index_name", textFormatter.joinNames(filtered, properties, null));
		
		Collections.sort(filtered, addrLevelComparator);
		Collections.sort(addrJsonRow, addrLevelComparator);
		
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
	
	protected JSONObject createBoundaryAddrRow(List<JSONObject> result) {
		JSONObject fullAddressRow = new JSONObject();

		List<JSONObject> filtered = filterForFullText(result);

		fullAddressRow.put("index_name",  textFormatter.joinBoundariesNames(filtered, null));
		
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

class OSMRUAddressesLevelsMatcher extends AddressesLevelsMatcherImpl {
	
	public OSMRUAddressesLevelsMatcher(AddrLevelsComparator lelvelsComparator,
		NamesMatcher namesMatcher, List<String> placeBoundaries) {
		
		super(lelvelsComparator, namesMatcher, placeBoundaries);
		
	}
		
	public JSONObject hnAsJSON(JSONObject addrPoint, JSONObject addrRow) {
		JSONObject hnAddrPart = new JSONObject();
		
		String hn = addrRow.optString("addr:housenumber");
		
		hn = formatHN(hn, addrRow.optString("addr:letter", null));
		
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
	
	private formatHN(String hn, String letter) {
		hn = StringUtils.replace(hn, " к", " корпус");
		hn = StringUtils.replace(hn, " c", " строение");
		return "дом " + hn + (StringUtils.isNotBlank(letter) ? (" литер " + letter) : "");
	}
}

