package me.osm.gazetteer.psqlsearch.imp;

import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import me.osm.gazetteer.psqlsearch.imp.Importer.ImportException;
import me.osm.gazetteer.psqlsearch.named_jdbc_stmnt.NamedParameterPreparedStatement;
import me.osm.gazetteer.psqlsearch.query.IndexAnalyzer;
import me.osm.gazetteer.psqlsearch.query.IndexAnalyzer.Token;

public class ImportObjectParser {
	
	private static final boolean DO_ASCII = false;
	private static final boolean noSQLBatch = false;
	
	private IndexAnalyzer indexAnalyzer = new IndexAnalyzer();

	protected boolean addFeature(final NamedParameterPreparedStatement stmt, JSONObject jsonObject) throws ImportException {
		String type = jsonObject.getString("type");
			
		try {
			
			String osm_type = jsonObject.optString("osm_type");
			
			if ("mtainf".equals(type) || osm_type == null) {
				return false;
			}
			
			int osm_id = jsonObject.getInt("osm_id");
			
			JSONObject addrObject = jsonObject.optJSONObject("address");
			
			String fullText = getAddrFullText(addrObject);
			String localityName = jsonObject.optString("locality_name");
			List<Token> localityTokens = indexAnalyzer.normalizeLocationName(localityName, DO_ASCII);

			String housenumber = jsonObject.optString("housenumber");
			String streetName = jsonObject.optString("street_name");
			List<Token> streetTokens = indexAnalyzer.normalizeStreetName(streetName, DO_ASCII);
			
			JSONObject optTags = jsonObject.optJSONObject("tags");
			String name = optTags != null ? optTags.optString("name") : null;
			
			List<Token> nameTokens = indexAnalyzer.normalizeName(name, false);
			List<Token> nameAltTokens = indexAnalyzer.normalizeName(getAltNames(jsonObject), false);
			
			float baseScore = 1.0f;
			
			if ("hghnet".equals(type) || "hghway".equals(type)) {
				if (intersects(requiredTokens(nameTokens), requiredTokens(localityTokens))) {
					// Such highways is a huge pain in the butt
					// I'm thinking to throw it away or at least mark them as garbadge
					baseScore /= 10;
				}
			}

			boolean isPoi = "poipnt".equals(type);
			if (isPoi || notEmpty(jsonObject)) {
				
				
				// TODO
				// admin0_name
				// admin1_name
				// admin2_name
				// local_admin_name
				
				fillCommonField(stmt, jsonObject, type, osm_type, osm_id);
				
				stmt.setString("full_text", fullText);
				
				stmt.setString("name", asTSVector(requiredTokens(nameTokens)));
				stmt.setString("name_opt", asTSVector(optionalTokens(nameTokens)));
				stmt.setString("name_alt", asTSVector(requiredTokens(nameAltTokens)));
				
				stmt.setInt("hn_number", parseHousenumber(housenumber));
				stmt.setString("hn_exact", StringUtils.stripToNull(housenumber));

				if (StringUtils.isNotBlank(housenumber)) {
					Collection<String> hnVariants = indexAnalyzer.getHNVariants(housenumber);
					Array array = asTextArray(stmt, hnVariants);
					stmt.setArray("hn_array", array);
				}
				else {
					stmt.setArray("hn_array", null);
				}
				
				stmt.setString("street", asTSVector(requiredTokens(streetTokens)));
				stmt.setString("street_opt", asTSVector(optionalTokens(streetTokens)));
				
				stmt.setString("locality", asTSVector(requiredTokens(localityTokens)));
				stmt.setString("locality_opt", asTSVector(optionalTokens(localityTokens)));
				
				stmt.setString("locality_type", "city");

				stmt.setString("neighbourhood", null);
				
				fillLonLat(stmt, jsonObject);
				
				if (isPoi) {
					setPoiClasses(stmt, jsonObject);
					setPoiKeywords(stmt, jsonObject);
				}
				else {
					// TODO
					stmt.setString("addr_schema", "regular");
					// TODO
					stmt.setString("hm_match", "exact");
				}

				// TODO
				stmt.setDouble("base_score", baseScore);
				
				fillRefs(stmt, jsonObject);
				
				DateTime dateTimeTimestamp = new DateTime(jsonObject.getString("timestamp"));
				stmt.setTimestamp("created", new Timestamp(dateTimeTimestamp.getMillis()));
				stmt.setString("obj", jsonObject.toString());

				if (noSQLBatch) {
					stmt.execute();
					return false;
				}
				else {
					stmt.addBatch();
					return true;
				}
				
			}
			return false;
			
		}
		catch (JSONException je) {
			je.printStackTrace();
		}
		catch (SQLException se) {
			SQLException firstException = se.getNextException();
			if (firstException != null) {
				throw new ImportException(firstException);
			}
			throw new ImportException(se);
		}
		return false;
	}
	
	private int parseHousenumber(String housenumber) {
		String string = StringUtils.stripToNull(housenumber);
		if (string != null) {
			
		}
		return -1;
	}

	private boolean intersects(List<Token> nameTokens, List<Token> localityNameTokens) {
		Set<String> localityStringTokens = new HashSet<>();
		for (Token t : localityNameTokens) {
			localityStringTokens.add(t.token);
		}
		
		for (Token t : nameTokens) {
			if (localityStringTokens.contains(t.token)) {
				return true;
			}
		}
		
		return false;
	}

	private void setPoiClasses(final NamedParameterPreparedStatement stmt, JSONObject jsonObject) throws SQLException {
		JSONArray classesJSON = jsonObject.getJSONArray("poi_class");
		List<String> classes= readJSONTextArray(classesJSON);
		stmt.setArray("poi_class", asTextArray(stmt, classes));
	}

	private void setPoiKeywords(final NamedParameterPreparedStatement stmt, JSONObject jsonObject) throws SQLException {
		JSONArray poiKeywords = jsonObject.getJSONArray("poi_keywords");
		List<String> keywords = readJSONTextArray(poiKeywords);
		stmt.setArray("keywords", asTextArray(stmt, keywords));
	}

	private List<String> readJSONTextArray(JSONArray poiKeywords) {
		List<String> keywords = new ArrayList<>();
		for(int i = 0; i < poiKeywords.length(); i++) {
			keywords.add(poiKeywords.getString(i));
		}
		return keywords;
	}

	private Array asTextArray(final NamedParameterPreparedStatement stmt, 
			Collection<String> collection) throws SQLException {
		
		return stmt.getConnection().createArrayOf("text", collection.toArray());
	}

//	private void fillNearestStreet(final NamedParameterPreparedStatement stmt, JSONObject jsonObject, String streetName,
//			String streetTSVector) throws SQLException {
//		String nearest_stret = getNearestStreetName(jsonObject, streetName);
//		if (streetName.equals(nearest_stret)) {
//			stmt.setString("nearest_stret", streetTSVector);
//		}
//		else {
//			String nearestStreetTSVector = asTSVector(
//					indexAnalyzer.normalizeStreetName(nearest_stret, true));
//			stmt.setString("nearest_stret", nearestStreetTSVector);
//		}
//	}

	private void fillLonLat(final NamedParameterPreparedStatement stmt, JSONObject jsonObject) throws SQLException {
		JSONObject centroid = jsonObject.getJSONObject("center_point");
		stmt.setDouble("lon", (float)centroid.getDouble("lon"));
		stmt.setDouble("lat", (float)centroid.getDouble("lat"));
	}

	private void fillRefs(final NamedParameterPreparedStatement stmt, JSONObject jsonObject) throws SQLException {
		Map<String, String> refsMap = 
				getJsonObjectAsKVMap(jsonObject.optJSONObject("refs"), true);
		
		stmt.setString("refs", asHStore(refsMap));
	}

	private String asHStore(Map<String, String> refsMap) {
		
		if (refsMap.isEmpty()) {
			return "";
		}
		
		StringBuilder sb = new StringBuilder();
		
		for(Entry<String, String> entry : refsMap.entrySet()) {
			String key = StringUtils.remove(entry.getKey(), "=>");
			String val = StringUtils.remove(entry.getValue(), "=>");
			
			key = StringUtils.remove(key, '"');
			val = StringUtils.remove(val, '"');
			
			sb.append(',').append('"').append(key).append('"')
				.append("=>")
				.append('"').append(val).append('"');
		}
		
		return sb.substring(1);
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> getJsonObjectAsKVMap(JSONObject subj, boolean invertKV) {
		Map<String, String> result = new HashMap<>();
		
		if (subj != null) {
			for (Iterator<String> i = subj.keys(); i.hasNext();) {
				String key = i.next();
				
				Object valueObj = subj.get(key);
				if (valueObj instanceof JSONArray) {
					JSONArray array = (JSONArray)valueObj;
					if(invertKV) {
						for (int j = 0; j < array.length(); j++) {
							String value = array.getString(j);
							result.put(value, key);
						}
					}
				}
				else {
					String value = subj.getString(key);
					if (invertKV) {
						result.put(value, key);
					}
					else {
						result.put(key, value);
					}
				}
			}
		}
		
		return result;
	}

	private String getAddrFullText(JSONObject addrObject) {
		return addrObject != null ? addrObject.optString("longText") : null;
	}

	private String getNearestStreetName(JSONObject jsonObject, String streetName) {
		String nearest_stret = streetName;
		JSONArray nrbStrts = jsonObject.optJSONArray("nearby_streets");
		if (nrbStrts != null && nrbStrts.length() > 0) {
			JSONObject closestStreet = nrbStrts.getJSONObject(0);
			nearest_stret = closestStreet.getString("name");
		}
		return nearest_stret;
	}

	private boolean notEmpty(JSONObject jsonObject) {
		
		JSONObject addrObject = jsonObject.optJSONObject("address");
		String fullText = getAddrFullText(addrObject);
		String localityName = jsonObject.optString("locality_name");
		String housenumber = jsonObject.optString("housenumber");
		String streetName = jsonObject.optString("street_name");
		
		return fullText != null && (housenumber != null || streetName != null || localityName != null);
	}

	private List<Token> requiredTokens(List<Token> tokens) {
		List<Token> result = new ArrayList<>();
		for (Token t : tokens) {
			if(!t.optional) {
				result.add(t);
			}
		}
		return result;
	}
	
	private List<Token> optionalTokens(List<Token> tokens) {
		List<Token> result = new ArrayList<>();
		for (Token t : tokens) {
			if(t.optional) {
				result.add(t);
			}
		}
		return result;
	}
	
	private String asTSVector(List<Token> tokens) {
		if (tokens == null || tokens.isEmpty()) {
			return "";
		}
		
		StringBuilder tsvector = new StringBuilder();
		for(Token t : tokens) {
			String token = StringUtils.replaceChars(t.token, ":;\"'&|", null);
			tsvector.append(' ').append(token);
		}
		
		return tsvector.substring(1);
	}

	@SuppressWarnings("unchecked")
	private String getAltNames(JSONObject jsonObject) {
		JSONObject tags = jsonObject.optJSONObject("tags");

		if (tags != null) {
			List<String> names = new ArrayList<>();
			
			for (Iterator<String> iterator = tags.keys(); iterator.hasNext();) {
				String key = iterator.next();
				if (!"name".equals(key) && StringUtils.contains(key, "name")) {
					names.add(tags.getString(key));
				}
			}
			
			if(!names.isEmpty()) {
				return StringUtils.join(names, ' ');
			}
		}
		
		return null;
	}

	private void fillCommonField(NamedParameterPreparedStatement stmt, 
			JSONObject jsonObject, String type, String osm_type, int osm_id) throws SQLException {
		
		stmt.setString("id", jsonObject.getString("id"));
		stmt.setString("type", type);
		stmt.setString("feature_id", jsonObject.getString("feature_id"));
		
		stmt.setLong("osm_id",  osm_id);
		stmt.setString("osm_type", String.valueOf(osm_type.charAt(0)));
	}

}
