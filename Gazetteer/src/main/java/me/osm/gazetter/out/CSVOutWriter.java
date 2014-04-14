package me.osm.gazetter.out;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import me.osm.gazetter.Options;
import me.osm.gazetter.join.Joiner;
import me.osm.gazetter.join.PoiAddrJoinBuilder;
import me.osm.gazetter.striper.FeatureTypes;
import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.utils.FileUtils;
import me.osm.gazetter.utils.FileUtils.LineHandler;
import me.osm.osmdoc.read.OSMDocFacade;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.supercsv.io.CsvListWriter;
import org.supercsv.prefs.CsvPreference;

import com.google.code.externalsorting.ExternalSort;

public class CSVOutWriter implements LineHandler {
	
	public static final Map<String, String> ARG_TO_TYPE = new LinkedHashMap<>();
	static {
		ARG_TO_TYPE.put("address", FeatureTypes.ADDR_POINT_FTYPE);
		ARG_TO_TYPE.put("street", FeatureTypes.HIGHWAY_FEATURE_TYPE);
		ARG_TO_TYPE.put("place", FeatureTypes.PLACE_POINT_FTYPE);
		ARG_TO_TYPE.put("poi", FeatureTypes.POI_FTYPE);
	}
	
	public static Comparator<String> defaultcomparator = new Comparator<String>() {
		@Override
		public int compare(String r1, String r2) {
			return r1.compareTo(r2);
		}
	};

	private String dataDir;
	private List<List<String>> columns;
	private Set<String> types;
	
	private Map<String, CsvListWriter> writers = new HashMap<>();
	
	private PrintWriter out;
	
	private FeatureValueExtractor featureEXT = new FeatureValueExctractorImpl();
	private FeatureValueExtractor poiEXT;
	private AddrRowValueExtractor addrRowEXT = new AddrRowValueExctractorImpl();
	
	private Set<String> addrRowKeys = new HashSet<String>(addrRowEXT.getSupportedKeys());
	private Set<String> allSupportedKeys = new HashSet<String>(featureEXT.getSupportedKeys());

	private OSMDocFacade osmDocFacade;
	
	private CSVOutLineHandler outLineHandler = null;

	public CSVOutWriter(String dataDir, String columns, List<String> types, String out, 
			String poiCatalog) {
		
		allSupportedKeys.addAll(addrRowKeys);
		
		this.dataDir = dataDir;
		this.columns = parseColumns(columns);
		this.types = new HashSet<>();
		
		//checkColumnsKeys();
		
		try {
			for(String type : types) {
				
				String ftype = ARG_TO_TYPE.get(type);
				this.types.add(ftype);
				writers.put(ftype, new CsvListWriter(
						new PrintWriter(getFile4Ftype(ftype)), 
						new CsvPreference.Builder('$', '\t', "\n").build()));
			}

			if("-".equals(out)) {
				this.out = new PrintWriter(System.out);
			}
			else {
				this.out = new PrintWriter(new File(out));
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		osmDocFacade = new OSMDocFacade(poiCatalog, null);
		poiEXT = new PoiValueExctractorImpl(osmDocFacade);
		
		outLineHandler = Options.get().getCsvOutLineHandler();
	}

	private void checkColumnsKeys() {
		boolean keyNotFound = false;
		for(List<String> c : this.columns) {
			for(String s : c) {
				if(!this.allSupportedKeys.contains(s)) {
					System.out.println("Unsupported column key " + s);
					keyNotFound = true;
				}
			}
		}
		if(keyNotFound) {
			List<String> keys = new ArrayList<>(allSupportedKeys);
			Collections.sort(keys);
			System.out.println("Supported keys are:");
			for(String k : keys) {
				System.out.println("\t" + k);
			}
			System.exit(1);
		}
	}

	private File getFile4Ftype(String ftype) {
		return new File(this.dataDir + "/" + ftype + ".csv.tmp");
	}

	private List<List<String>> parseColumns(String columns) {
		
		StringTokenizer tokenizer = new StringTokenizer(columns, " ,;[]", true);
		List<List<String>> result = new ArrayList<>();
		
		boolean inner = false;
		List<String> innerList = new ArrayList<>();
		
		while (tokenizer.hasMoreTokens()) {
			String token = tokenizer.nextToken();
			if(!" ".equals(token) && !",".equals(token) && !";".equals(token)) {
				if("[".equals(token)) {
					inner = true;
				}
				else if("]".equals(token)) {
					inner = false;
					result.add(innerList);
					innerList = new ArrayList<>();
				}
				else {
					if(inner) {
						innerList.add(token);
					}
					else {
						result.add(Arrays.asList(token));
					}
				}
			}
		}
		
		if(!innerList.isEmpty()) {
			result.add(innerList);
		}
		return result;
	}

	public void write() {
		File folder = new File(dataDir);
		try {
			for(File stripeF : folder.listFiles(Joiner.STRIPE_FILE_FN_FILTER)) {
				FileUtils.handleLines(stripeF, this);
			}
			
			for(CsvListWriter w : writers.values()) {
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
		List<String> types = new ArrayList<>(this.types);
		Collections.sort(types);
		for(String type : types) {
			File sorted = new File(this.dataDir + "/" + type + ".csv.sorted");
			sort(getFile4Ftype(type), sorted);
			FileUtils.handleLines(sorted, new LineHandler() {
				@Override
				public void handle(String s) {
					out.println(s);					
				}
			});
			sorted.delete();
		}		
	}

	private void sort(File in, File out) throws IOException {
            List<File> l = ExternalSort.sortInBatch(in, defaultcomparator,
                    100, Charset.defaultCharset(), new File(dataDir), true, 0,
                    false);

           ExternalSort.mergeSortedFiles(l, out, defaultcomparator, Charset.defaultCharset(),
                    true, false, false);
                    
           in.delete();    
	}

	@Override
	public void handle(String line) {
		String ftype = GeoJsonWriter.getFtype(line);
		
		if(types.contains(ftype)) {

			JSONObject jsonObject = new JSONObject(line);

			if(FeatureTypes.ADDR_POINT_FTYPE.equals(ftype)) {
				JSONArray addresses = jsonObject.optJSONArray("addresses");
				if(addresses != null) {
					for(int ri = 0; ri < addresses.length(); ri++ ) {
						List<Object> row = new ArrayList<>();
						JSONObject addrRow = addresses.getJSONObject(ri);
						Map<String, JSONObject> mapLevels = mapLevels(addrRow);
						
						for (List<String> column : columns) {
							row.add(getColumn(ftype, jsonObject, mapLevels, addrRow, column));
						}
						
						if(outLineHandler != null) {
							if(outLineHandler.handle(row, ftype, jsonObject, mapLevels, addrRow)) {
								writeNext(row, ftype);
							}
						}
						else {
							writeNext(row, ftype);
						}
						
					}
				}
			}
			else if(FeatureTypes.HIGHWAY_FEATURE_TYPE.equals(ftype)) {
				JSONArray boundaries = jsonObject.optJSONArray("boundaries");
				if(boundaries != null) {
					for(int i = 0; i < boundaries.length(); i++) {
						JSONObject bs = boundaries.getJSONObject(i);
						Map<String, JSONObject> mapLevels = mapLevels(bs);
						List<Object> row = new ArrayList<>();
						
						for (List<String> column : columns) {
							row.add(getColumn(ftype, jsonObject, mapLevels, bs, column));
						}
						
						if(outLineHandler != null) {
							if(outLineHandler.handle(row, ftype, jsonObject, mapLevels, bs)) {
								writeNext(row, ftype);
							}
						}
						else {
							writeNext(row, ftype);
						}
					}
				}
			}
			else if(FeatureTypes.PLACE_POINT_FTYPE.equals(ftype)) {
				JSONObject boundaries = jsonObject.optJSONObject("boundaries");
				if(boundaries != null) {
					Map<String, JSONObject> mapLevels = mapLevels(boundaries);
					List<Object> row = new ArrayList<>();
					
					for (List<String> column : columns) {
						row.add(getColumn(ftype, jsonObject, mapLevels, boundaries, column));
					}
					
					if(outLineHandler != null) {
						if(outLineHandler.handle(row, ftype, jsonObject, mapLevels, boundaries)) {
							writeNext(row, ftype);
						}
					}
					else {
						writeNext(row, ftype);
					}
					
				}
			}
			else if(FeatureTypes.POI_FTYPE.equals(ftype)) {
				List<JSONObject> addresses = getPoiAddresses(jsonObject);
				if(addresses != null) {
					for(JSONObject addrRow : addresses) {
						List<Object> row = new ArrayList<>();
						Map<String, JSONObject> mapLevels = mapLevels(addrRow);
						
						for (List<String> column : columns) {
							row.add(getColumn(ftype, jsonObject, mapLevels, addrRow, column));
						}
						
						if(outLineHandler != null) {
							if(outLineHandler.handle(row, ftype, jsonObject, mapLevels, addrRow)) {
								writeNext(row, ftype);
							}
						}
						else {
							writeNext(row, ftype);
						}
						
					}
				}
			}
			else {
				List<Object> row = new ArrayList<>();
				
				for (List<String> column : columns) {
					row.add(getColumn(ftype, jsonObject, null, null, column));
				}
				
				if(outLineHandler != null) {
					if(outLineHandler.handle(row, ftype, jsonObject, null, null)) {
						writeNext(row, ftype);
					}
				}
				else {
					writeNext(row, ftype);
				}
			}
			
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

	private void writeNext(List<Object> row, String ftype) {
		try {
			writers.get(ftype).write(row);
		} catch (IOException e) {
			throw new RuntimeException("Can't write row: " + row, e);
		}
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

	private Object getColumn(String ftype, JSONObject jsonObject, 
			Map<String, JSONObject> addrRowLevels, JSONObject addrRow, List<String> column) {
		for(String key : column) {

			Object value = null;
			if(addrRowKeys.contains(key)) {
				value = addrRowEXT.getValue(key, jsonObject, addrRowLevels, addrRow);
			}
			else {
				if(FeatureTypes.POI_FTYPE.equals(ftype)) {
					value = poiEXT.getValue(key, jsonObject);
				}
				else {
					value = featureEXT.getValue(key, jsonObject);
				}
			}
			
			if(value instanceof String) {
				value = StringUtils.stripToNull((String) value);
			}
			
			if(value != null) {
				return value;
			}
		}
		return null;
	}
	
}
