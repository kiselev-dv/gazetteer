package me.osm.gazetteer.web;

import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.osm.gazetteer.web.utils.FileUtils;
import me.osm.gazetteer.web.utils.FileUtils.LineHandler;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.ar.ArabicAnalyzer;
import org.apache.lucene.util.CollectionUtil;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequestBuilder;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.client.AdminClient;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.Node;
import org.json.JSONArray;
import org.json.JSONObject;

import com.sun.xml.fastinfoset.util.ContiguousCharArrayArray;


public class Importer implements LineHandler {
	
	private static final int BATCH_SIZE = 1000;

	public static void main(String[] args) {
		new Importer(args[0]).go();
	}
	
	public static class StripeFilenameFilter implements FilenameFilter {
		
		public boolean accept(File dir, String name) {
			return name.startsWith("stripe");
		}
	
	}
	
	public static final StripeFilenameFilter STRIPE_FILE_FN_FILTER = new StripeFilenameFilter();
	
	private String stripeFolder;
	private File folder;

	private Client client;

	private BulkRequestBuilder bulkRequest;
	
	public Importer(String stripeFolder) {
		this.stripeFolder = stripeFolder;
		this.folder = new File(this.stripeFolder);
		
		client = ESNodeHodel.getClient();
		bulkRequest = client.prepareBulk();
	}

	public void createIndex() {
		
		AdminClient admin = client.admin();
		
		String source;
		try {
			source = IOUtils.toString(getClass().getResourceAsStream("gazetteer.json"));
		} catch (IOException e) {
			throw new RuntimeException("couldn't read index settings", e);
		} 
		Settings settings = ImmutableSettings.builder().loadFromSource(source).build();
		
		admin.indices().create(new CreateIndexRequest("gazetteer", settings)).actionGet();
		
	}
	
	public void go() {
		
		try {
			IndicesExistsResponse response = 
					new IndicesExistsRequestBuilder(client.admin().indices())
						.setIndices("gazetteer").execute().actionGet();
			
			if(!response.isExists()) {
				createIndex();
			}
		
			for(File stripeF : folder.listFiles(STRIPE_FILE_FN_FILTER)) {
				FileUtils.handleLines(stripeF, this);
			}
		}
		finally {
			close();
		}
	}

	private void close() {
		if(bulkRequest.numberOfActions() > 0) {
			BulkResponse bulkResponse = bulkRequest.execute().actionGet();

			if (bulkResponse.hasFailures()) {
				throw new RuntimeException(bulkResponse.buildFailureMessage());
			}
			
		}
	}

	private static final Set<String> handledTypes = new HashSet<String>(Arrays.asList(
			FeatureTypes.ADDR_POINT_FTYPE,
			FeatureTypes.HIGHWAY_FEATURE_TYPE,
			FeatureTypes.PLACE_POINT_FTYPE,
			FeatureTypes.POI_FTYPE
	));
	
	public void handle(String s) {
		if(s != null) {
			JSONObject feature = new JSONObject(s);
			
			String ftype = feature.getString("ftype");
			if(handledTypes.contains(ftype)) {
					
				if(ftype.equals(FeatureTypes.ADDR_POINT_FTYPE) && !feature.has("addresses")) {
					return;
				}

				if((ftype.equals(FeatureTypes.HIGHWAY_FEATURE_TYPE) || ftype.equals(FeatureTypes.PLACE_POINT_FTYPE) ) 
						&& !feature.has("boundaries")) {
					return;
				}
				
				if(ftype.equals(FeatureTypes.POI_FTYPE) && !feature.has("joinedAddresses")) {
					return;
				}
				
				JSONObject result = new JSONObject();
				
				List<JSONObject> addresses = null;
				if(ftype.equals(FeatureTypes.ADDR_POINT_FTYPE)) {
					addresses = clearAddrLevels(asObjList(feature.getJSONArray("addresses")), feature);
				}
				else if(ftype.equals(FeatureTypes.HIGHWAY_FEATURE_TYPE) || ftype.equals(FeatureTypes.PLACE_POINT_FTYPE)) {
					Object b = feature.get("boundaries");
					if(b instanceof JSONObject) {
						addresses =  clearAddrLevels(Arrays.asList((JSONObject)b), feature);
					}
					else if (b instanceof JSONArray) {
						addresses = clearAddrLevels(asObjList((JSONArray)b), feature);
					}
				}
				else if(ftype.equals(FeatureTypes.POI_FTYPE)) {
					addresses =  clearAddrLevels(getPoiAddresses(feature), feature);
				}
				
				for(JSONObject address : addresses) {

					result.put("id", address.getString("id"));
					result.put("feature_id", feature.getString("id"));
					result.put("type", ftype);
					result.put("timestamp", feature.getString("timestamp"));
					
					//name
					//alt_names
					if(ftype.equals(FeatureTypes.ADDR_POINT_FTYPE)) {
						result.put("name", address.optString("name"));
						result.put("alt_names", address.optString("alt_names"));
					}
					else {
						JSONObject prop = feature.optJSONObject("properties");
						
						result.put("name", prop.optString("name"));
						
						JSONArray altNames = new JSONArray();
						for(Iterator<String> keys = prop.keys(); keys.hasNext();) {
							String key = keys.next();
							if(!key.equals("name") && key.contains("name")) {
								altNames.put(prop.getString(key));
							}
						}
						result.put("alt_names", altNames);
					}
					
					result.put("address", address.optString("name"));
					result.put("alt_addresses", address.optString("alt_names"));
					result.put("scheme", address.optString("scheme"));
					result.put("parts", address.optString("parts"));
					
					result.put("tags", feature.optJSONObject("properties"));
					
					if(ftype.equals(FeatureTypes.ADDR_POINT_FTYPE)) {
						putNearbyStreets(feature, result);
						
						result.put("nearest_city", asIdNameNames(feature.optJSONObject("nearestCity")));
						result.put("nearest_neighbour", asIdNameNames(feature.optJSONObject("nearestNeighbour")));
					}
					
					//geometry
					result.put("center_point", feature.get("geometry"));
					
					JSONObject meta = feature.getJSONObject("metainfo");
					if(meta.has("fullGeometry")) {
						
						result.put("full_geometry", meta.get("fullGeometry"));
					}
					
					//poi
					if(FeatureTypes.POI_FTYPE.equals(ftype)) {
						
						result.put("poi_class", feature.get("poiTypes"));
						
						JSONObject prop = feature.getJSONObject("properties");
						result.put("operator", prop.optString("operator"));
						result.put("brand", prop.optString("brand"));
						result.put("opening_hours", prop.optString("opening_hours"));
						
						result.put("phone", prop.has("contact:phone") ? 
								prop.optString("contact:phone") : prop.optString("phone"));
						
						result.put("email", prop.has("contact:email") ? 
								prop.optString("contact:email") : prop.optString("email"));
						
						result.put("website", prop.has("contact:website") ? 
								prop.optString("contact:website") 
								: prop.has("website") ? prop.optString("website") 
										: prop.optString("contact:facebook"));
					}
					
					add(result);
				}
				
			}
		}
	}

	private void add(JSONObject result) {
		if(bulkRequest == null) {
			bulkRequest = client.prepareBulk();
		}
		
		IndexRequestBuilder ind = new IndexRequestBuilder(client)
			.setSource(result.toString()).setIndex("gazetteer").setType("feature");
		bulkRequest.add(ind.request());
		
		if(bulkRequest.numberOfActions() % BATCH_SIZE == 0) {
			BulkResponse bulkResponse = bulkRequest.execute().actionGet();
			if (bulkResponse.hasFailures()) {
				throw new RuntimeException(bulkResponse.buildFailureMessage());
			}
			
			bulkRequest = client.prepareBulk();
		}
	}

	private JSONArray valuesAsJSONArray(JSONObject optJSONObject) {
		JSONArray result = new JSONArray(); 
		if(optJSONObject != null) {
			for(String key : (Set<String>)optJSONObject.keySet()) {
				result.put(optJSONObject.get(key));
			}
		}
		
		return result;
	}

	private JSONObject asIdNameNames(JSONObject src) {
		JSONObject result = new JSONObject();
		if(src != null) {
			result.put("id", src.getString("id"));
			
			JSONObject prop = src.optJSONObject("properties");
			if(prop != null) {
				result.put("name", prop.optString("name"));
			}
			
			JSONArray altNames = new JSONArray();
			for(Iterator<String> keys = prop.keys(); keys.hasNext();) {
				String key = keys.next();
				if(!key.equals("name") && key.contains("name")) {
					altNames.put(prop.getString(key));
				}
			}
			result.put("alt_names", altNames);
		}
		
		return result;
	}


	private List<JSONObject> clearAddrLevels(List<JSONObject> addressesSrc, JSONObject feature) {
		
		List<JSONObject> result = new ArrayList<JSONObject>();
		
		for(JSONObject addr : addressesSrc) {
			JSONObject addrResult = new JSONObject();
			
			result.add(addrResult);
			
			addrResult.put("name", addr.getString("text"));
			addrResult.put("id", feature.getString("id") + "-" + addr.optString("addr-scheme", "regular"));
			
			Set<String> langs = new HashSet<String>(asStringList(addr.getJSONArray("langs")));
			
			JSONArray addrNames = new JSONArray();
			for(String lang : asStringList(addr.getJSONArray("langs"))) {
				addrNames.put(addr.optString("text:" + lang));
			}
			addrResult.put("alt_names", addrNames);
			
			addrResult.put("scheme", addr.optString("addr-scheme"));
			
			JSONArray parts = new JSONArray();
			for(JSONObject partSrc : asObjList(addr.getJSONArray("parts"))) {
				
				JSONObject part = new JSONObject();
				part.put("name", part.optString("name"));
				
				JSONArray altNames = new JSONArray();
				part.put("alt_name", altNames);

				JSONObject names = partSrc.optJSONObject("names");
				if(names != null) {
					for(String key : new ArrayList<>((Set<String>) names.keySet())) {

						if(key.contains(":")) {
							for(String ps : Arrays.asList(StringUtils.split(key, ':'))) {
								if(langs.contains(ps)) {
									altNames.put(names.getString(key));
								}
							}
						}
						else if(!key.equals("name")) {
							altNames.put(names.getString(key));
						}
						
					}
				}
				
				part.put("lvl", partSrc.getString("lvl"));
				part.put("lvl-size", partSrc.getInt("lvl-size"));
				
				parts.put(part);
			}
			
			addrResult.put("parts", parts);
		}
		
		return result;
		
	}

	private void putNearbyStreets(JSONObject feature, JSONObject result) {
		List<JSONObject> nearbyStreets = asObjList(feature.optJSONArray("nearbyStreets"));
		JSONArray nrbStrts = new JSONArray();
		
		for(JSONObject obj : nearbyStreets) {
			JSONObject street = new JSONObject();
			street.put("id", obj.getString("id"));
			
			JSONObject prop = obj.optJSONObject("properties");
			if(prop != null) {
				street.put("name", prop.opt("name"));
				
				JSONArray names = new JSONArray();
				for(String key : (Set<String>)prop.keySet()) {
					
					if(!key.equals("name") && key.contains("name")) {
						names.put(prop.getString(key));
					}
					
				}
				
				street.put("alt_names", names);
				
			}
			nrbStrts.put(street);
		}
		
		result.put("nearby_streets", nrbStrts);
	}

	private List<String> asStringList(JSONArray jsonArray) {
		if(jsonArray != null) {
			
			List<String> result = new ArrayList<String>();
			
			for(int i = 0; i < jsonArray.length(); i++) {
				result.add(jsonArray.getString(i));
			}
			
			return result;
		}
		
		return Collections.emptyList();
	}

	private List<JSONObject> asObjList(JSONArray jsonArray) {
		
		if(jsonArray != null) {
			
			List<JSONObject> result = new ArrayList<JSONObject>();
			
			for(int i = 0; i < jsonArray.length(); i++) {
				result.add(jsonArray.getJSONObject(i));
			}
			
			return result;
		}
		
		return Collections.emptyList();
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
	
}
