package me.osm.gazetter.join.out_handlers;

import static me.osm.gazetter.join.out_handlers.GazetteerSchemeConstants.GAZETTEER_SCHEME_ADDRESS;
import static me.osm.gazetter.join.out_handlers.GazetteerSchemeConstants.GAZETTEER_SCHEME_ADDR_LEVEL;
import static me.osm.gazetter.join.out_handlers.GazetteerSchemeConstants.GAZETTEER_SCHEME_CENTER_POINT;
import static me.osm.gazetter.join.out_handlers.GazetteerSchemeConstants.GAZETTEER_SCHEME_FEATURE_ID;
import static me.osm.gazetter.join.out_handlers.GazetteerSchemeConstants.GAZETTEER_SCHEME_FULL_GEOMETRY;
import static me.osm.gazetter.join.out_handlers.GazetteerSchemeConstants.GAZETTEER_SCHEME_ID;
import static me.osm.gazetter.join.out_handlers.GazetteerSchemeConstants.GAZETTEER_SCHEME_NEARBY_PLACES;
import static me.osm.gazetter.join.out_handlers.GazetteerSchemeConstants.GAZETTEER_SCHEME_NEARBY_STREETS;
import static me.osm.gazetter.join.out_handlers.GazetteerSchemeConstants.GAZETTEER_SCHEME_NEAREST_NEIGHBOUR;
import static me.osm.gazetter.join.out_handlers.GazetteerSchemeConstants.GAZETTEER_SCHEME_NEAREST_PLACE;
import static me.osm.gazetter.join.out_handlers.GazetteerSchemeConstants.GAZETTEER_SCHEME_POI_ADDR_MATCH;
import static me.osm.gazetter.join.out_handlers.GazetteerSchemeConstants.GAZETTEER_SCHEME_POI_CLASS;
import static me.osm.gazetter.join.out_handlers.GazetteerSchemeConstants.GAZETTEER_SCHEME_POI_KEYWORDS;
import static me.osm.gazetter.join.out_handlers.GazetteerSchemeConstants.GAZETTEER_SCHEME_POI_TYPE_NAMES;
import static me.osm.gazetter.join.out_handlers.GazetteerSchemeConstants.GAZETTEER_SCHEME_REFS;
import static me.osm.gazetter.join.out_handlers.GazetteerSchemeConstants.GAZETTEER_SCHEME_TAGS;
import static me.osm.gazetter.join.out_handlers.GazetteerSchemeConstants.GAZETTEER_SCHEME_TIMESTAMP;
import static me.osm.gazetter.join.out_handlers.GazetteerSchemeConstants.GAZETTEER_SCHEME_TYPE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import me.osm.gazetter.addresses.AddressesUtils;
import me.osm.gazetter.out.AddrRowValueExctractorImpl;
import me.osm.gazetter.striper.FeatureTypes;
import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.striper.JSONFeature;
import me.osm.gazetter.utils.LocatePoint;
import me.osm.osmdoc.model.Feature;
import me.osm.osmdoc.read.DOCFileReader;
import me.osm.osmdoc.read.DOCFolderReader;
import me.osm.osmdoc.read.DOCReader;
import me.osm.osmdoc.read.OSMDocFacade;
import me.osm.osmdoc.read.tagvalueparsers.TagsStatisticCollector;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;

public class GazetteerOutWriter extends AddressPerRowJOHBase  {
	
	private static final TagsStatisticCollector tagStatistics = new TagsStatisticCollector();

	public static final String NAME = "out-gazetteer";

	private OSMDocFacade osmDocFacade;
	
	private List<String> localAdminKeys;
	private List<String> localityKeys;
	private List<String> neighborhoodKeys;

	private boolean exportAllNames = false;

	private DOCReader reader;
	
	@Override
	public JoinOutHandler newInstance(List<String> options) {
		
		HandlerOptions paresdOpts = HandlerOptions.parse(options, 
				Arrays.asList("out", "export_all_names", "local_admin", "locality", "neighborhood", "poi_catalog"));
		
		this.exportAllNames = "true".equals(paresdOpts.getString("export_all_names", "true"));
		
		localAdminKeys = list(paresdOpts.getList("local_admin"));
		if(localAdminKeys == null || localAdminKeys.isEmpty()) {
			localAdminKeys = Arrays.asList("boundary:6");
		}
		
		localityKeys = list(paresdOpts.getList("locality"));
		if(localityKeys == null || localityKeys.isEmpty()) {
			localityKeys = Arrays.asList("place:city", "place:town", "place:village", "place:hamlet", "boundary:8");
		}
		
		neighborhoodKeys = list(paresdOpts.getList("neighborhood"));
		if(neighborhoodKeys == null || neighborhoodKeys.isEmpty()) {
			neighborhoodKeys = Arrays.asList("place:town", "place:village", "place:hamlet", "place:neighbour", "boundary:9", "boundary:10");
		}
		
		String poiCatalogPath = paresdOpts.getString("poi_catalog", "jar");
		
		if(poiCatalogPath.endsWith(".xml") || poiCatalogPath.equals("jar")) {
			reader = new DOCFileReader(poiCatalogPath);
		}
		else {
			reader = new DOCFolderReader(poiCatalogPath);
		}
		
		osmDocFacade = new OSMDocFacade(reader, null);
		
		if(paresdOpts.has(null)) {
			initializeWriter(paresdOpts.getString(null, null));
		}
		else {
			initializeWriter(paresdOpts.getString("out", null));
		}
		
		return this;
	}
	
	@Override
	protected void handlePoiPointAddrRow(JSONObject object, JSONObject address,
			String stripe) {
		
		// skip pois with empty addresses
		if(address == null) {
			return;
		}
		
		JSONFeature result = new JSONFeature();
		fillObject(result, address, object);
		fillPOI(result, object, address.getString("poiAddrMatch"));
		
		println(result.toString());
		flush();
	}
	
	@Override
	protected void handleAddrNodeAddrRow(JSONObject object, JSONObject address,
			String stripe) {
		
		JSONFeature result = new JSONFeature();
		fillObject(result, address, object);
		println(result.toString());
		flush();
	}
	
	@Override
	protected void handleHighwayAddrRow(JSONObject object, JSONObject address,
			String stripe) {
		
		JSONFeature result = new JSONFeature();
		fillObject(result, address, object);
		println(result.toString());
		flush();
	}
	
	@Override
	protected void handlePlaceBoundaryAddrRow(JSONObject object, JSONObject address,
			String stripe) {
		
		if(address == null) {
			return;
		}
		
		JSONFeature result = new JSONFeature();
		fillObject(result, address, object);
		println(result.toString());
		flush();
	}
	
	@Override
	protected void handlePlacePointAddrRow(JSONObject object,
			JSONObject address, String stripe) {
		JSONFeature result = new JSONFeature();
		fillObject(result, address, object);
		println(result.toString());
		flush();
	}
	
	@Override
	protected void handleAdminBoundaryAddrRow(JSONObject object,
			JSONObject address, String stripe) {
		JSONFeature result = new JSONFeature();
		fillObject(result, address, object);
		println(result.toString());
		flush();
	}
	
	protected void fillObject(JSONFeature result, JSONObject addrRow, JSONObject jsonObject) {
		
		String ftype = jsonObject.getString("ftype");
		String rowId = AddrRowValueExctractorImpl.getUID(jsonObject, addrRow, ftype);
		
		result.put(GAZETTEER_SCHEME_ID, rowId);
		result.put(GAZETTEER_SCHEME_FEATURE_ID, jsonObject.getString("id"));
		result.put(GAZETTEER_SCHEME_TYPE, ftype);
		result.put(GAZETTEER_SCHEME_TIMESTAMP, jsonObject.getString("timestamp"));
		
		putAddress(result, addrRow);
		Set<String> langs = putAltAddresses(result, addrRow);
		
		Map<String, JSONObject> mapLevels = mapLevels(addrRow);

		putName(result, ftype, mapLevels, jsonObject, addrRow);
		putAltNames(result, ftype, mapLevels, jsonObject, addrRow);
		putNameTranslations(result, ftype, mapLevels, jsonObject, addrRow, langs);
		if(exportAllNames) {
			result.put("all_names", new JSONObject(AddressesUtils.filterNameTags(jsonObject)));
		}

		putNearbyStreets(result, ftype, mapLevels, jsonObject, langs);
		putNearbyPlaces(result, ftype, mapLevels, jsonObject, langs);
		
		JSONObject refs = new JSONObject();
		
		String minLVL = putAddrParts(result, refs, addrRow, mapLevels, langs);
		result.put(GAZETTEER_SCHEME_REFS, refs);
		
		if(minLVL != null) {
			result.put(GAZETTEER_SCHEME_ADDR_LEVEL, minLVL);
		}
		
		JSONObject properties = jsonObject.optJSONObject("properties");
		if(properties != null) {
			result.put(GAZETTEER_SCHEME_TAGS, properties);
		}
		
		JSONObject centroid = getCentroid(jsonObject, ftype);
		if(centroid != null) {
			result.put(GAZETTEER_SCHEME_CENTER_POINT, centroid);
		}
		
		JSONObject geom = getFullGeometry(jsonObject, ftype); 
		if(geom != null) {
			Geometry g = GeoJsonWriter.parseGeometry(geom);
			if(g != null && g.isValid()) {
				
				if(geom != null) {
					String esGeomType = geom.getString(GAZETTEER_SCHEME_TYPE).toLowerCase();
					geom.put(GAZETTEER_SCHEME_TYPE, esGeomType);
				}
				
				result.put(GAZETTEER_SCHEME_FULL_GEOMETRY, geom);
			}
		}
		
	}

	protected void fillPOI(JSONFeature result, JSONObject jsonObject,
			String poiAddrMatch) {
		
		JSONArray typesArray = jsonObject.getJSONArray("poiTypes");
		JSONObject tags = jsonObject.getJSONObject("properties");

		result.put(GAZETTEER_SCHEME_POI_CLASS, typesArray);
		
		List<Feature> poiClassess = new ArrayList<Feature>();
		for(int i = 0; i < typesArray.length(); i++) {
			Feature poiClass = osmDocFacade.getFeature(typesArray.getString(i));
			if(poiClass != null) {
				poiClassess.add(poiClass);
			}
		} 
		
		if(poiClassess.isEmpty()) {
			return;
		}
	
		result.put(GAZETTEER_SCHEME_POI_TYPE_NAMES, 
				new JSONArray(osmDocFacade.listPoiClassNames(poiClassess)));

		JSONArray poiAddrRefs = new JSONArray();
		
		if(poiAddrMatch != null) {
			result.put(GAZETTEER_SCHEME_POI_ADDR_MATCH, poiAddrMatch);
			
			if(!"boundaries".equals(poiAddrMatch)) {
				Object matchedAddresses = jsonObject.getJSONObject("joinedAddresses").opt(poiAddrMatch);
				
				if(matchedAddresses instanceof JSONObject) {
					poiAddrRefs.put(((JSONObject)matchedAddresses).getString("id"));
				}
				else if(matchedAddresses instanceof JSONArray) {
					JSONArray maa = (JSONArray) matchedAddresses;
					for(int i=0; i<maa.length();i++) {
						poiAddrRefs.put(maa.getJSONObject(i).getString("id"));
					}
				}
			}
		}
		
		result.getJSONObject("refs").put("poi_addresses", poiAddrRefs);
		
		JSONObject moreTags = osmDocFacade.parseMoreTags(poiClassess, tags, tagStatistics);
		result.put("more_tags", moreTags);

		//TODO Keywords
		LinkedHashSet<String> keywords = new LinkedHashSet<String>();
		osmDocFacade.collectKeywords(poiClassess, moreTags, keywords);
		result.put(GAZETTEER_SCHEME_POI_KEYWORDS, new JSONArray(keywords));
	}

	protected JSONObject getCentroid(JSONObject jsonObject, String ftype) {
		
		JSONObject result = new JSONObject();
		if(FeatureTypes.HIGHWAY_FEATURE_TYPE.equals(ftype)) {
			
			LineString ls = GeoJsonWriter.getLineStringGeometry(
					jsonObject.getJSONObject(GeoJsonWriter.GEOMETRY)
						.getJSONArray(GeoJsonWriter.COORDINATES));
			
			Coordinate c = new LocatePoint(ls, 0.5).getPoint();
			result.put("lon", c.x);
			result.put("lat", c.y);
		}
		else {
			JSONArray coords = jsonObject.getJSONObject(GeoJsonWriter.GEOMETRY)
					.getJSONArray(GeoJsonWriter.COORDINATES);
			
			result.put("lon", coords.getDouble(0));
			result.put("lat", coords.getDouble(1));
		}
		
		return result;
	}

	protected JSONObject getFullGeometry(JSONObject jsonObject, String ftype) {
		JSONObject fullGeometry = null;
		
		if(FeatureTypes.PLACE_POINT_FTYPE.equals(ftype)) {
			JSONObject matchedBoundary = jsonObject.optJSONObject("matchedBoundary");
			if(matchedBoundary != null) {
				fullGeometry = matchedBoundary.getJSONObject(GeoJsonWriter.META).optJSONObject(GeoJsonWriter.FULL_GEOMETRY);
			}
		}
		else {
			JSONObject meta = jsonObject.getJSONObject(GeoJsonWriter.META);
			fullGeometry = meta.optJSONObject("fullGeometry");
		}
		
		return fullGeometry;
	}

	protected String putAddrParts(JSONObject result, JSONObject refs,
			JSONObject addrRow, Map<String, JSONObject> mapLevels, 
			Set<String> langs) {
		
		String minLvl = null;
		
		if(mapLevels == null) {
			return null;
		}
		
		JSONObject admin0 = mapLevels.get("boundary:2");
		putAddrLevel(result, refs, langs, admin0, "admin0");
		if(admin0 != null) {
			minLvl = "admin0";
		}

		JSONObject admin1 = mapLevels.get("boundary:3");
		putAddrLevel(result, refs, langs, admin1, "admin1");
		if(admin1 != null) {
			minLvl = "admin1";
		}

		JSONObject admin2 = mapLevels.get("boundary:4");
		putAddrLevel(result, refs, langs, admin2, "admin2");
		if(admin2 != null) {
			minLvl = "admin2";
		}

		JSONObject local_admin = getNotNull(mapLevels, localAdminKeys, null);
		putAddrLevel(result, refs, langs, local_admin, "local_admin");
		if(local_admin != null) {
			minLvl = "local_admin";
		}

		JSONObject locality = getNotNull(mapLevels, localityKeys, local_admin);
		putAddrLevel(result, refs, langs, locality, "locality");
		if(locality != null) {
			minLvl = "locality";
		}

		JSONObject neighborhood = getNotNull(mapLevels, neighborhoodKeys, locality);
		putAddrLevel(result, refs, langs, neighborhood, "neighborhood");
		if(neighborhood != null) {
			minLvl = "neighborhood";
		}

		JSONObject street = mapLevels.get("street");
		putAddrLevel(result, refs, langs, street, "street");
		if(street != null) {
			minLvl = "street";
		}
		
		JSONObject hn = mapLevels.get("hn");
		if(hn != null) {
			result.put("housenumber", hn.optString("name"));
		}
		
		if(hn != null) {
			minLvl = "housenumber";
		}
		
		return minLvl;
	}

	protected JSONObject getNotNull(Map<String, JSONObject> mapLevels,
			List<String> levels, JSONObject upper) {
		
		for(String lvl : levels) {
			if(mapLevels.get(lvl) != null) {
				if(upper != null) {
					String upperId = getAddrPartId(upper);
					String thisId = getAddrPartId(mapLevels.get(lvl));
					
					if(!upperId.equals(thisId)) {
						return mapLevels.get(lvl);
					}
				}
				else {
					return mapLevels.get(lvl);
				}
			}
		}
		
		return null;
	}

	protected String getAddrPartId(JSONObject upper) {
		String upperId = upper.optString("lnk");

		if(StringUtils.stripToNull(upperId) == null) {
			upperId = upper.optString("name");
		}
		
		return upperId;
	}

	protected void putAddrLevel(JSONObject result, JSONObject refs,
			Set<String> langs, JSONObject admin0, String key) {
		if(admin0 != null) {
			
			String name = admin0.optString("name");
			if(StringUtils.isNotBlank(name)) {
				
				result.put(key + "_name", name);
				
				JSONObject namesHash = admin0.optJSONObject("names");
				Map<String, String> names = AddressesUtils.filterNameTags(namesHash);
				names.remove("name");
				
				filterNamesByLangs(names, langs);
				if(!names.isEmpty()) {
					result.put(key + "_alternate_names", new JSONArray(names.values()));
				}
				
				JSONObject translations = AddressesUtils.getNamesTranslations(namesHash, langs);
				if(translations != null && translations.length() > 0) {
					result.put(key + "_name_trans", translations);
				}
			}
			
			String lnk = admin0.optString("lnk");
			if(StringUtils.isNotEmpty(lnk)) {
				refs.put(key, lnk);
			}
		}
	}

	protected void filterNamesByLangs(Map<String, String> names, Set<String> langs) {
		if(names != null) {
			Iterator<Entry<String, String>> iterator = names.entrySet().iterator();
			while (iterator.hasNext()) {
				Entry<String, String> entry = iterator.next();
				String key = entry.getKey();
				if(key.contains(":")) {
					String[] split = StringUtils.split(key, ':');
					for(String s : split) {
						if(langs.contains(s)) {
							continue;
						}
					}
					iterator.remove();
				}
			}
		}
	}

	protected Set<String> putAltAddresses(JSONObject result, JSONObject addrRow) {
		
		Set<String> langsSet = new HashSet<String>();
		
		if(addrRow != null) {
			JSONArray langs = addrRow.optJSONArray("langs");
			
			if(langs != null && langs.length() > 0) {
				
				List<String> altAddresses = new ArrayList<String>();
				Map<String, String> altAddressesHash = new HashMap<String, String>();
				
				for(int i = 0; i < langs.length(); i++) {
					String lang = langs.optString(i);
					if(StringUtils.isNotBlank(lang)) {
						langsSet.add(lang);
						String altText = addrRow.optString("text:" + lang);
						if(StringUtils.isNotBlank(altText)) {
							altAddresses.add(altText);
							altAddressesHash.put(lang, altText);
						}
					}
				}
				
				if(!altAddresses.isEmpty()) {
					result.put("alt_addresses", new JSONArray(altAddresses));
					result.put("alt_addresses_trans", new JSONObject(altAddressesHash));
				}
			}
		}
		
		return langsSet;
	}

	protected void putAddress(JSONObject result, JSONObject addrRow) {
		if(addrRow != null) {
			String addrText = addrRow.optString("text", null);
			if(addrText != null) {
				result.put(GAZETTEER_SCHEME_ADDRESS, addrText);
			}
		}
	}

	protected void putNearbyPlaces(JSONObject result, String ftype,
			Map<String, JSONObject> mapLevels, JSONObject jsonObject, Set<String> langs) {

		if(jsonObject.has("nearestCity")) {
			JSONObject nearestCitySRC = jsonObject.getJSONObject("nearestCity");
			String placeString = nearestCitySRC.getJSONObject("properties").optString("place");
			if(StringUtils.isNotBlank(placeString)) {
				JSONObject place = asIdNameNames(nearestCitySRC, langs);
				if(place != null) {
					place.put("place", placeString);
					if(place.has(GAZETTEER_SCHEME_ID)) {
						place.put(GAZETTEER_SCHEME_ID, StringUtils.replace(place.getString(GAZETTEER_SCHEME_ID), 
								FeatureTypes.PLACE_DELONEY_FTYPE, FeatureTypes.PLACE_POINT_FTYPE));
					}
					result.put(GAZETTEER_SCHEME_NEAREST_PLACE, place);
				}
			}
		}

		if(jsonObject.has("nearestNeighbour")) {
			JSONObject nearestCitySRC = jsonObject.getJSONObject("nearestNeighbour");
			String placeString = nearestCitySRC.getJSONObject("properties").optString("place");
			if(StringUtils.isNotBlank(placeString)) {
				JSONObject place = asIdNameNames(nearestCitySRC, langs);
				if(place != null) {
					place.put("place", placeString);
					if(place.has(GAZETTEER_SCHEME_ID)) {
						place.put(GAZETTEER_SCHEME_ID, StringUtils.replace(place.getString(GAZETTEER_SCHEME_ID), 
								FeatureTypes.PLACE_DELONEY_FTYPE, FeatureTypes.PLACE_POINT_FTYPE));
					}
					result.put(GAZETTEER_SCHEME_NEAREST_NEIGHBOUR, place);
				}
			}
		}
		
		if(jsonObject.has("neighbourCities")) {
			List<JSONObject> list = new ArrayList<JSONObject>();
			JSONArray jsonArray = jsonObject.getJSONArray("neighbourCities");
			for(int i = 0; i < jsonArray.length(); i++) {
				JSONObject placeSRC = jsonArray.getJSONObject(i);
				String placeString = placeSRC.getJSONObject("properties").optString("place");
				JSONObject place = asIdNameNames(placeSRC, langs);
				if(place != null) {
					place.put("place", placeString);
					if(place.has(GAZETTEER_SCHEME_ID)) {
						place.put(GAZETTEER_SCHEME_ID, StringUtils.replace(place.getString(GAZETTEER_SCHEME_ID), 
								FeatureTypes.PLACE_DELONEY_FTYPE, FeatureTypes.PLACE_POINT_FTYPE));
					}
					list.add(place);
				}
			}
			result.put(GAZETTEER_SCHEME_NEARBY_PLACES, new JSONArray(list));
		}
	}

	protected void putNearbyStreets(JSONObject result, String ftype,
			Map<String, JSONObject> mapLevels, JSONObject jsonObject, Set<String> langs) {
		if(jsonObject.has("nearbyStreets")) {
			JSONArray streetsSRC = jsonObject.getJSONArray("nearbyStreets");
			
			if(streetsSRC.length() > 0) {
				JSONArray streets = new JSONArray();
				for(int i = 0; i < streetsSRC.length(); i++) {
					JSONObject streetSRC = streetsSRC.getJSONObject(i);
					JSONObject street = asIdNameNames(streetSRC, langs);
					if(street != null) {
						JSONObject properties = streetSRC.optJSONObject("properties");
						street.put("highway", properties.optString("highway"));
						streets.put(street);
					}
				}
				result.put(GAZETTEER_SCHEME_NEARBY_STREETS, streets);
			}
		}
	}

	protected JSONObject asIdNameNames(JSONObject src, Set<String> langs) {
		JSONObject result = new JSONObject();
		
		result.put(GAZETTEER_SCHEME_ID, src.getString("id"));
		
		JSONObject properties = src.optJSONObject("properties");
		Map<String, String> nameTags = AddressesUtils.filterNameTags(properties);
		if(nameTags.containsKey("name")) {
			result.put("name", nameTags.get("name"));
			
			nameTags.remove("name");
			if(!nameTags.isEmpty()) {
				result.put("alt_names", new JSONArray(nameTags.values()));
			}
			
			JSONObject translations = AddressesUtils.getNamesTranslations(properties, langs);
			if(translations != null && translations.length() > 0) {
				result.put("name_trans", translations);
			}
			
			return result;
		}
		
		return null;
	}

	protected void putNameTranslations(JSONObject result, String ftype,
			Map<String, JSONObject> mapLevels, JSONObject jsonObject, JSONObject addrRow, Set<String> langs) {
		
		if(!FeatureTypes.ADDR_POINT_FTYPE.equals(ftype))  {
			
			JSONObject properties = jsonObject.optJSONObject("properties");
			JSONObject translations = AddressesUtils.getNamesTranslations(properties, langs);
			
			if(translations != null && translations.length() > 0) {
				result.put("name_trans", translations);
			}
		}
	}

	protected void putAltNames(JSONObject result, String ftype,
			Map<String, JSONObject> mapLevels, JSONObject jsonObject, JSONObject addrRow) {
		
		if(!FeatureTypes.ADDR_POINT_FTYPE.equals(ftype))  {
			JSONObject properties = jsonObject.optJSONObject("properties");
			Map<String, String> altNames = AddressesUtils.filterNameTags(properties);
			altNames.remove("name");
			
			if(!altNames.isEmpty()) {
				result.put("alt_names", new JSONArray(altNames.values()));
			}
		}
	}

	protected void putName(JSONObject result, String ftype,
			Map<String, JSONObject> mapLevels, JSONObject jsonObject, JSONObject addrRow) {
		
		JSONObject properties = jsonObject.optJSONObject("properties");
		if(properties != null && properties.has("name")) {
			result.put("name", properties.getString("name"));
		}
		
	}


	protected Map<String, JSONObject> mapLevels(JSONObject addrRow) {
		try {
			if(addrRow == null) {
				return null;
			}

			Map<String, JSONObject> result = new HashMap<String, JSONObject>();
			
			JSONArray parts = addrRow.getJSONArray("parts"); 
			for(int i = 0; i < parts.length(); i++) {
				JSONObject part = parts.getJSONObject(i);
				result.put(part.getString("lvl"), part); 
			}
			
			return result;
		}
		catch (JSONException e) {
			return null;
		}
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static List<String> list( List list) {
		if(list == null) {
			return Collections.emptyList();
		}
		return list;
	}
}
