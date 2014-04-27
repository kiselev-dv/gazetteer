package me.osm.gazetter.out;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import me.osm.gazetter.striper.GeoJsonWriter;

import org.json.JSONObject;

public class AddrRowValueExctractorImpl implements AddrRowValueExtractor {

	private static final String BOUNDARY_2_ID = "boundary:2.id";
	private static final String BOUNDARY_2 = "boundary:2";
	private static final String BOUNDARY_3_ID = "boundary:3.id";
	private static final String BOUNDARY_3 = "boundary:3";
	private static final String BOUNDARY_4_ID = "boundary:4.id";
	private static final String BOUNDARY_4 = "boundary:4";
	private static final String BOUNDARY_5_ID = "boundary:5.id";
	private static final String BOUNDARY_5 = "boundary:5";
	private static final String BOUNDARY_6_ID = "boundary:6.id";
	private static final String BOUNDARY_6 = "boundary:6";
	private static final String BOUNDARY_8_ID = "boundary:8.id";
	private static final String BOUNDARY_8 = "boundary:8";
	private static final String PLACE_CITY_ID = "place:city.id";
	private static final String PLACE_CITY = "place:city";
	private static final String PLACE_TOWN_ID = "place:town.id";
	private static final String PLACE_TOWN = "place:town";
	private static final String PLACE_HAMLET_ID = "place:hamlet.id";
	private static final String PLACE_HAMLET = "place:hamlet";
	private static final String PLACE_VILLAGE_ID = "place:village.id";
	private static final String PLACE_VILLAGE = "place:village";
	private static final String PLACE_ISOLATED_DWELLING_ID = "place:isolated_dwelling.id";
	private static final String PLACE_ISOLATED_DWELLING = "place:isolated_dwelling";
	private static final String PLACE_LOCALITY_ID = "place:locality.id";
	private static final String PLACE_LOCALITY = "place:locality";
	private static final String PLACE_ALLOTMENTS_ID = "place:allotments.id";
	private static final String PLACE_ALLOTMENTS = "place:allotments";
	private static final String PLACE_SUBURB_ID = "place:suburb.id";
	private static final String PLACE_SUBURB = "place:suburb";
	private static final String PLACE_NEIGHBOURHOOD_ID = "place:neighbourhood.id";
	private static final String PLACE_NEIGHBOURHOOD = "place:neighbourhood";
	private static final String PLACE_QUARTER_ID = "place:quarter.id";
	private static final String PLACE_QUARTER = "place:quarter";
	private static final String STREET_ID = "street.id";
	private static final String STREET = "street";
	private static final String HOUSENUMBER = "hn";
	private static final String LETTER = "letter";
	private static final String POSTCODE = "postcode";
	private static final String ADDR_TEXT = "addr-text";
	private static final String ADDR_LONG_TEXT = "addr-long-text";

	@Override
	public String getValue(String key, JSONObject jsonObject, Map<String, JSONObject> levels, JSONObject addrRow) {
		
		if(levels == null) {
			return null;
		}
		
		try {
			
			if(ADDR_TEXT.equals(key)) {
				return addrRow.optString("text");
			}

			if(ADDR_LONG_TEXT.equals(key)) {
				return addrRow.optString("longText");
			}
			
			if(key.endsWith(".id")) {
				return levels.get(key.replace(".id", "")).getString("lnk");
			}

			if(key.endsWith(".lvl-size")) {
				return levels.get(key.replace(".lvl-size", "")).getString("lvl-size");
			}
			
			if(LETTER.equals(key)) {
				return jsonObject.getJSONObject(GeoJsonWriter.PROPERTIES).optString("addr:letter");
			}
			
			JSONObject lvl = levels.get(key);
			if(lvl != null && lvl.has("name")) {
				return lvl.getString("name");
			}
			
			return null;
			
		}
		catch (Exception e) {
			return null;
		}
		
	}

	@Override
	public Collection<String> getSupportedKeys() {
		return Arrays.asList(POSTCODE, LETTER, HOUSENUMBER, 
			STREET, STREET_ID, PLACE_QUARTER, PLACE_QUARTER_ID,
			PLACE_NEIGHBOURHOOD, PLACE_NEIGHBOURHOOD_ID, PLACE_SUBURB, 
			PLACE_SUBURB_ID, PLACE_ALLOTMENTS, PLACE_ALLOTMENTS_ID, 
			PLACE_LOCALITY, PLACE_LOCALITY_ID, PLACE_ISOLATED_DWELLING, 
			PLACE_ISOLATED_DWELLING_ID, PLACE_VILLAGE, PLACE_VILLAGE_ID,
			PLACE_HAMLET, PLACE_HAMLET_ID, PLACE_CITY, PLACE_CITY_ID,
			PLACE_TOWN, PLACE_TOWN_ID,
			BOUNDARY_8, BOUNDARY_8_ID, BOUNDARY_6, BOUNDARY_6_ID, 
			BOUNDARY_5, BOUNDARY_5_ID, BOUNDARY_4, BOUNDARY_4_ID,
			BOUNDARY_3, BOUNDARY_3_ID, BOUNDARY_2, BOUNDARY_2_ID,
			ADDR_TEXT, ADDR_LONG_TEXT);
	}

}
