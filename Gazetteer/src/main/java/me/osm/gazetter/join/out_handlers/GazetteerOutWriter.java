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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import me.osm.gazetter.addresses.AddressesUtils;
import me.osm.gazetter.join.util.ExportTagsStatisticCollector;
import me.osm.gazetter.out.AddrRowValueExctractorImpl;
import me.osm.gazetter.striper.FeatureTypes;
import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.striper.JSONFeature;
import me.osm.gazetter.utils.FileUtils;
import me.osm.gazetter.utils.JSONHash;
import me.osm.gazetter.utils.LocatePoint;
import me.osm.osmdoc.localization.L10n;
import me.osm.osmdoc.model.Feature;
import me.osm.osmdoc.model.Tag.Val;
import me.osm.osmdoc.read.DOCFileReader;
import me.osm.osmdoc.read.DOCFolderReader;
import me.osm.osmdoc.read.DOCReader;
import me.osm.osmdoc.read.OSMDocFacade;
import me.osm.osmdoc.read.tagvalueparsers.LogTagsStatisticCollector;
import me.osm.osmdoc.read.tagvalueparsers.TagsStatisticCollector;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.code.externalsorting.ExternalSort;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;

public class GazetteerOutWriter extends AddressPerRowJOHBase  {
	
	private static final String TRANSLATE_POI_TYPES_OPTION = "translate_poi_types";

	private static final List<String> OPTIONS = Arrays.asList(
			"out", "local_admin", "locality", 
			"neighborhood", "poi_catalog", 
			TRANSLATE_POI_TYPES_OPTION, 
			"fill_addresses", "export_all_names", 
			"full_geometry",
			"usage", "tag-stat");

	private TagsStatisticCollector tagStatistics;

	public static final String NAME = "out-gazetteer";

	private OSMDocFacade osmDocFacade;
	
	private List<String> localAdminKeys;
	private List<String> localityKeys;
	private List<String> neighborhoodKeys;

	private boolean exportAllNames = false;
	private boolean translatePOITypes = false;
	private boolean fullGeometry = true;

	private DOCReader reader;

	private Set<String> fillAddrOpts = null;

	private String tagStatPath;
	
	private String outFile;
	
	@Override
	public JoinOutHandler newInstance(List<String> options) {
		
		HandlerOptions parsedOpts = HandlerOptions.parse(options, OPTIONS);
		
		if(parsedOpts.has("usage")) {
			printUsage();
			System.exit(0);
		}
		
		translatePOITypes = parsedOpts.getFlag(TRANSLATE_POI_TYPES_OPTION, true, false);
		
		fillAddrOpts = new HashSet<String>(parsedOpts.getList("fill_addresses", 
				Arrays.asList("ref", "levels", "nearest", "obj", "trans", "alt_names")));
		
		exportAllNames = parsedOpts.getFlag("export_all_names", true, false);
		fullGeometry = parsedOpts.getFlag("full_geometry", true, true);
		
		localAdminKeys = parsedOpts.getList("local_admin", Arrays.asList("boundary:6"));
		
		localityKeys = parsedOpts.getList("locality", 
				Arrays.asList("place:city", "place:town", "place:village", "place:hamlet", "boundary:8"));
		
		neighborhoodKeys = parsedOpts.getList("locality", 
				Arrays.asList("place:town", "place:village", "place:hamlet", "place:neighbour", "boundary:9", "boundary:10"));
		
		String poiCatalogPath = parsedOpts.getString("poi_catalog", "jar");
		
		if(poiCatalogPath.endsWith(".xml") || poiCatalogPath.equals("jar")) {
			reader = new DOCFileReader(poiCatalogPath);
		}
		else {
			reader = new DOCFolderReader(poiCatalogPath);
		}
		
		osmDocFacade = new OSMDocFacade(reader, null);
		
		if(parsedOpts.has(null)) {
			initializeWriter(parsedOpts.getString(null, null));
		}
		else {
			initializeWriter(parsedOpts.getString("out", null));
		}
		
		tagStatPath = parsedOpts.getString("tag-stat", null);
		if(tagStatPath == null) {
			tagStatistics = new LogTagsStatisticCollector();
		}
		else {
			tagStatistics = new ExportTagsStatisticCollector();
		}
		
		return this;
	}
	
	protected void printUsage() {
		StringBuilder usage = new StringBuilder();
		usage.append("Usage: join --handlers ").append(NAME).append("[out_file]");
		
		int i = 0;
		for(String opt : OPTIONS) {
			usage.append(" ").append("[").append(opt).append("[=<val>]]");
			if(i%3 == 0 && i > 0) {
				usage.append("\n\t");
			}
			i++;
		}
		
		usage.append("\n");
		usage.append("\n");
		
		usage.append("\tout=<file | - > - File where to store results. Use - for stdout. ")
			.append("Files with .gz or .bz2 extensions will be compressed.");
		usage.append("\n");
		usage.append("\n");
		
		usage.append("\tlocal_admin=boundary:1 boundary:2 place:town - Boundary levels, which will be matched as local_admin addr level.");
		usage.append("\n");
		usage.append("\n");
		
		usage.append("\tlocality=... Same as local_admin. Boundary levels, which will be matched as locality addr level.");
		usage.append("\n");
		usage.append("\n");

		usage.append("\tneighborhood=... Same as local_admin. Boundary levels, which will be matched as neighborhood addr level.");
		usage.append("\n");
		usage.append("\n");

		usage.append("\tpoi_catalog=<jar | path> Path to file .xml or folder with osm-doc poi classificator.");
		usage.append("\n");
		usage.append("\n");

		usage.append("\ttranslate_poi_types [=true|false] Export or not poi types translations. False if missed.");
		usage.append("\n");
		usage.append("\n");

		usage.append("\texport_all_names [=true|false] Export or not all object tags with 'name' in key name. False if missed.");
		usage.append("\n");
		usage.append("\n");

		usage.append("\tfull_geometry [=true|false] Export objects' full geometry. True if missed.");
		usage.append("\n");
		usage.append("\n");

		usage.append("\tfill_addresses=[ref] [levels] [nearest] [obj] [trans] [alt_names] Which parts of addresses to fill.");
		usage.append("\n");
		usage.append("\t\tref - add ref object with ids of mathced objects for each addr level as ref.level");
		usage.append("\n");
		usage.append("\t\tlevels - Match addresses to levels or not.");
		usage.append("\n");
		usage.append("\t\tnearest - Export nearest and nearby places neighbours and streets.");
		usage.append("\n");
		usage.append("\t\tobj - Export original address row object.");
		usage.append("\n");
		usage.append("\t\ttrans - Translate or not names for addr parts.");
		usage.append("\n");
		usage.append("\t\talt_names - Export addr parts alternative names.");
		usage.append("\n");

		usage.append("\n");
		usage.append("\tusage Print this message and exit.");
		usage.append("\n");
		
		
		System.out.println(usage.toString());
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
	protected void handleHighwayNetAddrRow(JSONObject object, JSONObject address,
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
		if(StringUtils.contains(stripe, "binx")) {
			JSONFeature result = new JSONFeature();
			fillObject(result, address, object);
			println(result.toString());
			flush();
		}
	}
	
	/**
	 * Fill fields, common for all kind of objects
	 * */
	protected void fillObject(JSONFeature result, JSONObject addrRow, JSONObject jsonObject) {
		
		String ftype = jsonObject.getString("ftype");
		String rowId = AddrRowValueExctractorImpl.getUID(jsonObject, addrRow, ftype);
		
		result.put(GAZETTEER_SCHEME_ID, rowId);
		result.put(GAZETTEER_SCHEME_FEATURE_ID, jsonObject.getString("id"));
		result.put(GAZETTEER_SCHEME_TYPE, ftype);
		result.put(GAZETTEER_SCHEME_TIMESTAMP, jsonObject.getString("timestamp"));
		
		if(fillAddrOpts.contains("obj")) {
			result.put(GAZETTEER_SCHEME_ADDRESS, addrRow);
		}
		
		Set<String> langs = getLangs(addrRow);
		
		Map<String, JSONObject> mapLevels = mapLevels(addrRow);

		putName(result, ftype, mapLevels, jsonObject, addrRow);
		
		putAltNames(result, ftype, mapLevels, jsonObject, addrRow);
		putNameTranslations(result, ftype, mapLevels, jsonObject, addrRow, langs);
		
		if(exportAllNames) {
			result.put("all_names", new JSONObject(AddressesUtils.filterNameTags(jsonObject)));
		}

		if(fillAddrOpts.contains("nearest")) {
			putNearbyStreets(result, ftype, mapLevels, jsonObject, langs);
			putNearbyPlaces(result, ftype, mapLevels, jsonObject, langs);
		}
		
		if(fillAddrOpts.contains("levels") || fillAddrOpts.contains("ref")) {
			JSONObject refs = new JSONObject();
			
			String minLVL = putAddrParts(result, refs, addrRow, mapLevels, langs);
			
			if(fillAddrOpts.contains("ref")) {
				result.put(GAZETTEER_SCHEME_REFS, refs);
			}
			
			if(minLVL != null) {
				result.put(GAZETTEER_SCHEME_ADDR_LEVEL, minLVL);
			}
		}
		
		JSONObject properties = jsonObject.optJSONObject("properties");
		if(properties != null) {
			result.put(GAZETTEER_SCHEME_TAGS, properties);
		}
		
		JSONObject centroid = getCentroid(jsonObject, ftype);
		if(centroid != null) {
			result.put(GAZETTEER_SCHEME_CENTER_POINT, centroid);
		}
		
		if(FeatureTypes.HIGHWAY_NET_FEATURE_TYPE.equals(ftype)) {
			result.put("members", jsonObject.get("members"));
			result.put("geometries", jsonObject.get("geometries"));
		}
		
		if(fullGeometry) {
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
		
		String md5Hex = DigestUtils.md5Hex(JSONHash.asCanonicalString(result, new HashSet<String>(Arrays.asList("timestamp"))));
		result.put("md5", md5Hex);
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
	
		if(translatePOITypes) {
			
			JSONObject trans = new JSONObject();
			
			for(Feature f : poiClassess) {
				String className = f.getName();
				JSONObject classNameT = new JSONObject();
				String title = f.getTitle();
				for(String sl : L10n.supported) {
					classNameT.put(sl, L10n.tr(title, Locale.forLanguageTag(sl)));
				}
				trans.put(className, classNameT);
			}
			
			result.put(GAZETTEER_SCHEME_POI_TYPE_NAMES, trans);
		}

		
		
		fillPoiAddrRefs(result, jsonObject, poiAddrMatch);
		
		Map<String, List<Val>> moreTagsVals = new HashMap<String, List<Val>>();
		JSONObject moreTags = osmDocFacade.parseMoreTags(poiClassess, tags, 
				tagStatistics, moreTagsVals);
		
		result.put("more_tags", moreTags);

		LinkedHashSet<String> keywords = new LinkedHashSet<String>();
		osmDocFacade.collectKeywords(poiClassess, moreTagsVals, keywords, null);
		
		result.put(GAZETTEER_SCHEME_POI_KEYWORDS, new JSONArray(keywords));
	}

	protected void fillPoiAddrRefs(JSONFeature result, JSONObject jsonObject,
			String poiAddrMatch) {
		
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

			result.getJSONObject("refs").put("poi_addresses", poiAddrRefs);
		}
	}

	protected JSONObject getCentroid(JSONObject jsonObject, String ftype) {
		
		JSONObject result = new JSONObject();
		if(FeatureTypes.HIGHWAY_FEATURE_TYPE.equals(ftype)) {
			
			LineString ls = GeoJsonWriter.getLineStringGeometry(
					jsonObject.getJSONObject(GeoJsonWriter.GEOMETRY)
						.getJSONArray(GeoJsonWriter.COORDINATES));
			
			Coordinate c = new LocatePoint(ls, ls.getLength() * 0.5).getPoint();
			result.put("lon", c.x);
			result.put("lat", c.y);
		}
		else if (jsonObject.has("center_point")) {
			return jsonObject.getJSONObject("center_point");
		}
		else{
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
		if(hn != null && fillAddrOpts.contains("levels")) {
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
		if(admin0 != null && fillAddrOpts.contains("levels")) {
			
			String name = admin0.optString("name");
			if(StringUtils.isNotBlank(name)) {
				
				result.put(key + "_name", name);
				
				JSONObject namesHash = admin0.optJSONObject("names");
				Map<String, String> names = AddressesUtils.filterNameTags(namesHash);
				names.remove("name");
				
				filterNamesByLangs(names, langs);
				if(!names.isEmpty() && fillAddrOpts.contains("alt_names")) {
					result.put(key + "_alternate_names", new JSONArray(names.values()));
				}
				
				JSONObject translations = AddressesUtils.getNamesTranslations(namesHash, langs);
				if(translations != null && translations.length() > 0 && fillAddrOpts.contains("trans")) {
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

	protected Set<String> getLangs(JSONObject addrRow) {
		
		Set<String> langsSet = new HashSet<String>();
		
		if(addrRow != null) {
			JSONArray langs = addrRow.optJSONArray("langs");
			
			if(langs != null && langs.length() > 0) {
				
				for(int i = 0; i < langs.length(); i++) {
					String lang = langs.optString(i);
					if(StringUtils.isNotBlank(lang)) {
						langsSet.add(lang);
					}
				}
			}
		}
		
		return langsSet;
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
			if(!nameTags.isEmpty() && fillAddrOpts.contains("alt_names")) {
				result.put("alt_names", new JSONArray(nameTags.values()));
			}
			
			JSONObject translations = AddressesUtils.getNamesTranslations(properties, langs);
			if(translations != null && translations.length() > 0 && fillAddrOpts.contains("trans")) {
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
	
	@Override
	protected void initializeWriter(String file) {
		if(file == null) {
			System.out.println("There is no out file");
			printUsage();
		}
		
		this.outFile = file;
		super.initializeWriter(this.outFile);
	}
	
	@Override
	public void allDone() {
		
		super.allDone();

		try {
			File file = new File(this.outFile);
			BufferedReader fbr = new BufferedReader(new InputStreamReader(FileUtils.getFileIS(file)));
			
			List<File> batch = ExternalSort.sortInBatch(fbr, file.length(), new JSONByIdComparator(), 
					ExternalSort.DEFAULTMAXTEMPFILES, ExternalSort.estimateAvailableMemory(), 
					Charset.forName("utf-8"), null, true, 0, true);
			
			initializeWriter(outFile);
			
			ExternalSort.mergeSortedFiles(batch, new BufferedWriter(writer), 
					new JSONByIdComparator(), Charset.forName("utf-8"), true, true, ReduceHighwayNetworks.INSTANCE);
			
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		writeTagStat();
	}

	private void writeTagStat() {
		if(StringUtils.isNotBlank(tagStatPath)) {
			Collection<JSONObject> usage = ((ExportTagsStatisticCollector)tagStatistics).asJson();
			
			try {
				PrintWriter printwriter = FileUtils.getPrintwriter(new File(tagStatPath), false);
				
				for(JSONObject jo : usage) {
					printwriter.println(jo.toString());
				}
				
				printwriter.flush();
				printwriter.close();
				
			} catch (IOException e) {
				throw new RuntimeException();
			}
		}
	}
}
