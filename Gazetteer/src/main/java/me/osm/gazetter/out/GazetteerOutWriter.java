package me.osm.gazetter.out;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import me.osm.gazetter.addresses.AddressesUtils;
import me.osm.gazetter.join.Joiner;
import me.osm.gazetter.striper.FeatureTypes;
import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.striper.JSONFeature;
import me.osm.gazetter.utils.FileUtils;
import me.osm.gazetter.utils.FileUtils.LineHandler;
import me.osm.gazetter.utils.JSONHash;
import me.osm.gazetter.utils.LocatePoint;
import me.osm.osmdoc.model.Feature;
import me.osm.osmdoc.read.OSMDocFacade;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.code.externalsorting.ExternalSort;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;

import static me.osm.gazetter.out.GazetteerSchemeConstants.*;

public class GazetteerOutWriter  implements LineHandler  {
	
	private static final Set<String> hashIgnoreFields = new HashSet<String>(
			Arrays.asList(new String[]{GAZETTEER_SCHEME_TIMESTAMP}));

	private static final String GAZETTEER_SCHEME_NEARBY_STREETS = "nearby_streets";

	private static final String GAZETTEER_SCHEME_NEARBY_PLACES = "nearby_places";

	private static final String GAZETTEER_SCHEME_NEAREST_NEIGHBOUR = "nearest_neighbour";

	private static final String GAZETTEER_SCHEME_NEAREST_PLACE = "nearest_place";

	private static final String GAZETTEER_SCHEME_ADDRESS = "address";

	private String dataDir;
	
	private Map<String, PrintWriter> writers = new HashMap<>();
	
	private PrintWriter out;
	
	private OSMDocFacade osmDocFacade;
	
	private List<String> localAdminKeys;
	private List<String> localityKeys;
	private List<String> neighborhoodKeys;

	private boolean exportAllNames = false;
	
	public GazetteerOutWriter(String dataDir, String out, String poiCatalog, 
			List<String> localAdmin, List<String> locality, 
			List<String> neighborhood, boolean exportAllNames) {
		
		this.exportAllNames = exportAllNames;
		
		localAdminKeys = localAdmin;
		if(localAdminKeys == null || localAdminKeys.isEmpty()) {
			localAdminKeys = Arrays.asList("boundary:6");
		}

		localityKeys = locality;
		if(localityKeys == null || localityKeys.isEmpty()) {
			localityKeys = Arrays.asList("place:city", "place:town", "place:village", "place:hamlet", "boundary:8");
		}

		neighborhoodKeys = neighborhood;
		if(neighborhoodKeys == null || neighborhoodKeys.isEmpty()) {
			neighborhoodKeys = Arrays.asList("place:town", "place:village", "place:hamlet", "place:neighbour", "boundary:9", "boundary:10");
		}
		
		this.dataDir = dataDir;
		
		try {
			writers.put(FeatureTypes.POI_FTYPE, new PrintWriter(getFile4Ftype(FeatureTypes.POI_FTYPE), "UTF8"));
			writers.put(FeatureTypes.ADDR_POINT_FTYPE, new PrintWriter(getFile4Ftype(FeatureTypes.ADDR_POINT_FTYPE), "UTF8"));
			writers.put(FeatureTypes.HIGHWAY_FEATURE_TYPE, new PrintWriter(getFile4Ftype(FeatureTypes.HIGHWAY_FEATURE_TYPE), "UTF8"));
			writers.put(FeatureTypes.PLACE_POINT_FTYPE, new PrintWriter(getFile4Ftype(FeatureTypes.PLACE_POINT_FTYPE), "UTF8"));
			writers.put(FeatureTypes.PLACE_BOUNDARY_FTYPE, new PrintWriter(getFile4Ftype(FeatureTypes.PLACE_BOUNDARY_FTYPE), "UTF8"));
			writers.put(FeatureTypes.ADMIN_BOUNDARY_FTYPE, new PrintWriter(getFile4Ftype(FeatureTypes.ADMIN_BOUNDARY_FTYPE), "UTF8"));

			if("-".equals(out)) {
				this.out = new PrintWriter(new OutputStreamWriter(System.out, "UTF8"));
			}
			else {
				this.out = FileUtils.getPrintwriter(new File(out), false);
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		osmDocFacade = new OSMDocFacade(poiCatalog, null);
	}

	private File getFile4Ftype(String ftype) {
		return new File(this.dataDir + "/" + ftype + ".gjson.tmp");
	}

	public void write() {
		File folder = new File(dataDir);
		try {
			for(File stripeF : folder.listFiles(Joiner.STRIPE_FILE_FN_FILTER)) {
				FileUtils.handleLines(stripeF, this);
			}
			
			FileUtils.handleLines(FileUtils.withGz(new File(dataDir + "/binx.gjson")), new LineHandler() {
				
				@Override
				public void handle(String s) {
					if(s != null) {
						JSONObject jsonObject = new JSONObject(s);
						JSONObject boundaries = jsonObject.optJSONObject("boundaries");
						if(boundaries != null) {
							Map<String, JSONObject> mapLevels = mapLevels(boundaries);
							JSONFeature row = new JSONFeature();
							
							fillObject(row, FeatureTypes.ADMIN_BOUNDARY_FTYPE, boundaries, mapLevels, jsonObject);
							
							writeNext(row, FeatureTypes.ADMIN_BOUNDARY_FTYPE);
							
						}
					}
				}
				
			});
			
			for(PrintWriter w : writers.values()) {
				w.flush();
				w.close();
			}
			
			out();
			
			out.flush();
			out.close();		
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}


	private void out() throws IOException {
		for(String type : Arrays.asList(
				FeatureTypes.ADDR_POINT_FTYPE,
				FeatureTypes.HIGHWAY_FEATURE_TYPE,
				FeatureTypes.PLACE_POINT_FTYPE,
				FeatureTypes.PLACE_BOUNDARY_FTYPE,
				FeatureTypes.POI_FTYPE,
				FeatureTypes.ADMIN_BOUNDARY_FTYPE	
				)) {
			
			File sorted = new File(this.dataDir + "/" + type + ".gjson.sorted");
			sort(getFile4Ftype(type), sorted);
			FileUtils.handleLines(sorted, new LineHandler() {
				
				private String lastId = null;
				
				@Override
				public void handle(String s) {
					if(s != null) {
						String id = GeoJsonWriter.getId(s);
						if(!id.equals(lastId)) {
							out.println(s);					
						}
						lastId = id;
					}
				}
			});
			sorted.delete();
		}		
	}

	Comparator<String> featuresComparator = new Comparator<String>() {

		@Override
		public int compare(String paramT1, String paramT2) {
			String id1 = GeoJsonWriter.getId(paramT1);
			String id2 = GeoJsonWriter.getId(paramT2);
			
			return id1.compareTo(id2);
		}

	};
	
	private void sort(File in, File out) throws IOException {
            List<File> l = ExternalSort.sortInBatch(in, featuresComparator,
                    100, Charset.defaultCharset(), new File(dataDir), true, 0,
                    false);

           ExternalSort.mergeSortedFiles(l, out, featuresComparator, Charset.defaultCharset(),
                    true, false, false);
                    
           in.delete();    
	}

	private Set<String> types = new HashSet<String>(Arrays.asList(
			FeatureTypes.POI_FTYPE,
			FeatureTypes.ADDR_POINT_FTYPE,
			FeatureTypes.HIGHWAY_FEATURE_TYPE,
			FeatureTypes.PLACE_POINT_FTYPE));
	
	@Override
	public void handle(String line) {
		if(line == null) {
			return;
		}
		
		String ftype = GeoJsonWriter.getFtype(line);
		
		if(types.contains(ftype) && !FeatureTypes.ADMIN_BOUNDARY_FTYPE.equals(ftype)) {

			JSONObject jsonObject = new JSONObject(line);

			if(FeatureTypes.ADDR_POINT_FTYPE.equals(ftype)) {
				JSONArray addresses = jsonObject.optJSONArray("addresses");
				if(addresses != null) {
					for(int ri = 0; ri < addresses.length(); ri++ ) {
						JSONFeature row = new JSONFeature();
						JSONObject addrRow = addresses.getJSONObject(ri);
						Map<String, JSONObject> mapLevels = mapLevels(addrRow);
						
						fillObject(row, ftype, addrRow, mapLevels, jsonObject);
						
						writeNext(row, ftype);
					}
				}
			}
			else if(FeatureTypes.HIGHWAY_FEATURE_TYPE.equals(ftype)) {
				JSONArray boundaries = jsonObject.optJSONArray("boundaries");
				if(boundaries != null) {
					for(int i = 0; i < boundaries.length(); i++) {

						JSONFeature row = new JSONFeature();
						
						JSONObject bs = boundaries.getJSONObject(i);
						Map<String, JSONObject> mapLevels = mapLevels(bs);

						fillObject(row, ftype, bs, mapLevels, jsonObject);
						
						writeNext(row, ftype);
					}
				}
			}
			else if(FeatureTypes.PLACE_POINT_FTYPE.equals(ftype)) {
				JSONObject boundaries = jsonObject.optJSONObject("boundaries");
				if(boundaries != null) {

					JSONFeature row = new JSONFeature();
					Map<String, JSONObject> mapLevels = mapLevels(boundaries);
					
					fillObject(row, ftype, boundaries, mapLevels, jsonObject);
					
					writeNext(row, ftype);
					
				}
			}
			else if(FeatureTypes.POI_FTYPE.equals(ftype)) {
				List<JSONObject> addresses = getPoiAddresses(jsonObject);
				if(addresses != null) {
					for(JSONObject addrRow : addresses) {
						JSONFeature row = new JSONFeature();
						Map<String, JSONObject> mapLevels = mapLevels(addrRow);
						
						fillObject(row, ftype, addrRow, mapLevels, jsonObject);
						
						writeNext(row, ftype);
					}
				}
			}
			
		}
		
	}
	
	private void fillObject(JSONFeature result, String ftype, JSONObject addrRow,
			Map<String, JSONObject> mapLevels, JSONObject jsonObject) {
		
		String rowId = AddrRowValueExctractorImpl.getUID(jsonObject, addrRow, ftype);
		
		result.put(GAZETTEER_SCHEME_ID, rowId);
		result.put(GAZETTEER_SCHEME_FEATURE_ID, jsonObject.getString("id"));
		result.put(GAZETTEER_SCHEME_TYPE, ftype);
		result.put(GAZETTEER_SCHEME_TIMESTAMP, jsonObject.getString("timestamp"));
		
		putAddress(result, addrRow);
		Set<String> langs = putAltAddresses(result, addrRow);

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
		
		if(FeatureTypes.POI_FTYPE.equals(ftype)) {
			fillPOI(result, jsonObject, properties);
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
		
		result.put(GAZETTEER_SCHEME_MD5, DigestUtils.md5Hex(JSONHash.asCanonicalString(
				result, hashIgnoreFields)
		));
	}

	private void fillPOI(JSONFeature result, JSONObject jsonObject,
			JSONObject properties) {
		String poiType = jsonObject.getJSONArray("poiTypes").getString(0);
		result.put(GAZETTEER_SCHEME_POI_CLASS, poiType);
		
		Feature poiClass = osmDocFacade.getFeature(poiType);
		List<String> titles = osmDocFacade.listTranslatedTitles(poiClass);
		
		result.put(GAZETTEER_SCHEME_POI_CLASS_NAMES, new JSONArray(titles));
		
		String operator = properties.optString("operator");
		if(StringUtils.isNotEmpty(operator)) {
			result.put(GAZETTEER_SCHEME_OPERATOR, operator);
		}

		String brand = properties.optString("brand");
		if(StringUtils.isNotEmpty(brand)) {
			result.put(GAZETTEER_SCHEME_BRAND, brand);
		}

		String opening_hours = properties.optString("opening_hours");
		if(StringUtils.isNotEmpty(opening_hours)) {
			result.put(GAZETTEER_SCHEME_OPENING_HOURS, opening_hours);
		}

		String phone = properties.optString("contact:phone");
		if(StringUtils.isEmpty(phone)) {
			phone = properties.optString("phone");
		}
		if(StringUtils.isNotEmpty(phone)) {
			result.put(GAZETTEER_SCHEME_PHONE, phone);
		}

		String email = properties.optString("contact:email");
		if(StringUtils.isEmpty(email)) {
			phone = properties.optString("email");
		}
		if(StringUtils.isNotEmpty(email)) {
			result.put(GAZETTEER_SCHEME_EMAIL, email);
		}

		String website = properties.optString("contact:website");
		if(StringUtils.isEmpty(website)) {
			phone = properties.optString("website");
		}
		if(StringUtils.isNotEmpty(website)) {
			result.put(GAZETTEER_SCHEME_WEBSITE, website);
		}
		
		JSONArray jsonArray = jsonObject.optJSONArray("nearbyAddresses");
		if(jsonArray != null) {
			result.put(GAZETTEER_SCHEME_NEARBY_ADDRESSES, jsonArray);
		}
	}

	private JSONObject getCentroid(JSONObject jsonObject, String ftype) {
		
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

	private JSONObject getFullGeometry(JSONObject jsonObject, String ftype) {
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

	private String putAddrParts(JSONObject result, JSONObject refs,
			JSONObject addrRow, Map<String, JSONObject> mapLevels, 
			Set<String> langs) {
		
		String minLvl = null;
		
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

	private JSONObject getNotNull(Map<String, JSONObject> mapLevels,
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

	private String getAddrPartId(JSONObject upper) {
		String upperId = upper.optString("lnk");

		if(StringUtils.stripToNull(upperId) == null) {
			upperId = upper.optString("name");
		}
		
		return upperId;
	}

	private void putAddrLevel(JSONObject result, JSONObject refs,
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

	private void filterNamesByLangs(Map<String, String> names, Set<String> langs) {
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

	private Set<String> putAltAddresses(JSONObject result, JSONObject addrRow) {

		Set<String> langsSet = new HashSet<String>();
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
		
		return langsSet;
	}

	private void putAddress(JSONObject result, JSONObject addrRow) {
		String addrText = addrRow.optString("text", null);
		if(addrText != null) {
			result.put(GAZETTEER_SCHEME_ADDRESS, addrText);
		}
	}

	private void putNearbyPlaces(JSONObject result, String ftype,
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

	private void putNearbyStreets(JSONObject result, String ftype,
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

	private JSONObject asIdNameNames(JSONObject src, Set<String> langs) {
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

	private void putNameTranslations(JSONObject result, String ftype,
			Map<String, JSONObject> mapLevels, JSONObject jsonObject, JSONObject addrRow, Set<String> langs) {
		
		if(!FeatureTypes.ADDR_POINT_FTYPE.equals(ftype))  {
			
			JSONObject properties = jsonObject.optJSONObject("properties");
			JSONObject translations = AddressesUtils.getNamesTranslations(properties, langs);
			
			if(translations != null && translations.length() > 0) {
				result.put("name_trans", translations);
			}
		}
	}

	private void putAltNames(JSONObject result, String ftype,
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

	private void putName(JSONObject result, String ftype,
			Map<String, JSONObject> mapLevels, JSONObject jsonObject, JSONObject addrRow) {
		
		JSONObject properties = jsonObject.optJSONObject("properties");
		if(properties != null && properties.has("name")) {
			result.put("name", properties.getString("name"));
		}
		
	}

	private List<JSONObject> getPoiAddresses(JSONObject poi) {
		
		List<JSONObject> result = new ArrayList<>();
		JSONObject joinedAddresses = poi.optJSONObject("joinedAddresses");
		if(joinedAddresses != null) {
			
			//"sameSource"
			if(getAddressesFromObj(result, joinedAddresses, "sameSource")) {
				return result;
			}
			
			//"contains"
			if(getAddressesFromCollection(result, joinedAddresses, "contains")) {
				return result;
			}

			//"shareBuildingWay"
			if(getAddressesFromCollection(result, joinedAddresses, "shareBuildingWay")) {
				return result;
			}

			//"nearestShareBuildingWay"
			if(getAddressesFromCollection(result, joinedAddresses, "nearestShareBuildingWay")) {
				return result;
			}

			//"nearest"
			if(getAddressesFromObj(result, joinedAddresses, "nearest")) {
				return result;
			}
			
		}
		
		return result;
	}

	private boolean getAddressesFromObj(List<JSONObject> result,
			JSONObject joinedAddresses, String key) {
		
		boolean founded = false;
		
		JSONObject ss = joinedAddresses.optJSONObject(key);
		if(ss != null) {
			JSONArray addresses = ss.optJSONArray("addresses");
			if(addresses != null) {
				for(int i = 0; i < addresses.length(); i++) {
					result.add(addresses.getJSONObject(i));
				}
				founded = true;
			}
		}
		
		return founded;
	}

	private boolean getAddressesFromCollection(List<JSONObject> result,
			JSONObject joinedAddresses, String key) {
		
		boolean founded = false;
		
		JSONArray contains = joinedAddresses.optJSONArray("contains");
		if(contains != null && contains.length() > 0) {
			
			for(int ci = 0; ci < contains.length(); ci++) {
				JSONObject co = contains.getJSONObject(ci);
				JSONArray addresses = co.optJSONArray("addresses");
				if(addresses != null) {
					for(int i = 0; i < addresses.length(); i++) {
						result.add(addresses.getJSONObject(i));
						founded = true;
					}
				}
			}
			
		}
		
		return founded;
	}

	private void writeNext(JSONObject row, String ftype) {
		writers.get(ftype).println(row.toString());
	}

	private Map<String, JSONObject> mapLevels(JSONObject addrRow) {
		try {
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
}
