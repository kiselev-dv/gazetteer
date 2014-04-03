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

import me.osm.gazetter.join.Joiner;
import me.osm.gazetter.striper.FeatureTypes;
import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.utils.FileUtils;
import me.osm.gazetter.utils.FileUtils.LineHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.code.externalsorting.ExternalSort;

import au.com.bytecode.opencsv.CSVWriter;

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
	
	private Map<String, CSVWriter> writers = new HashMap<>();
	
	private PrintWriter out;
	
	private FeatureValueExtractor featureEXT = new FeatureValueExctractorImpl();
	private AddrRowValueExtractor addrRowEXT = new AddrRowValueExctractorImpl();
	private Set<String> addrRowKeys = new HashSet<String>(addrRowEXT.getSupportedKeys());
	
	public CSVOutWriter(String dataDir, String columns, List<String> types, String out) {
		
		this.dataDir = dataDir;
		this.columns = parseColumns(columns);
		this.types = new HashSet<>();
		
		
		try {
			for(String type : types) {
				String ftype = ARG_TO_TYPE.get(type);
				this.types.add(ftype);
				writers.put(ftype, new CSVWriter(new PrintWriter(getFile4Ftype(ftype))));
			}

			if("-".equals(out)) {
				this.out = new PrintWriter(System.out);
			}
			else {
				this.out = new PrintWriter(new File(out));
			}
		}
		catch (Exception e) {
			e.printStackTrace();
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
			
			for(CSVWriter w : writers.values()) {
				w.flush();
				w.close();
			}
			
			out();
			
			out.flush();
			out.close();		
		}
		catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
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

			int ci = 0;
			JSONObject jsonObject = new JSONObject(line);

			if(FeatureTypes.ADDR_POINT_FTYPE.equals(ftype)) {
				JSONArray addresses = jsonObject.optJSONArray("addresses");
				if(addresses != null) {
					for(int ri = 0; ri < addresses.length(); ri++ ) {
						ci = 0;
						String[] row = new String[columns.size()];
						JSONObject addrRow = addresses.getJSONObject(ri);
						Map<String, JSONObject> mapLevels = mapLevels(addrRow);
						
						for (List<String> column : columns) {
							row[ci++] = getColumn(jsonObject, mapLevels, addrRow, column);
						}
						writeNext(row, ftype);
					}
				}
			}
			else if(FeatureTypes.PLACE_POINT_FTYPE.equals(ftype) || FeatureTypes.HIGHWAY_FEATURE_TYPE.equals(ftype)) {
				JSONObject boundaries = jsonObject.optJSONObject("boundaries");
				if(boundaries != null) {
					Map<String, JSONObject> mapLevels = mapLevels(boundaries);
					String[] row = new String[columns.size()];
					
					for (List<String> column : columns) {
						row[ci++] = getColumn(jsonObject, mapLevels, boundaries, column);
					}
					writeNext(row, ftype);
				}
			}
			else {
				String[] row = new String[columns.size()];
				
				for (List<String> column : columns) {
					row[ci++] = getColumn(jsonObject, null, null, column);
				}
				writeNext(row, ftype);
			}
			
		}
		
	}

	
	
	private void writeNext(String[] row, String ftype) {
		writers.get(ftype).writeNext(row);
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

	private String getColumn(JSONObject jsonObject, Map<String, JSONObject> addrRowLevels, JSONObject addrRow, List<String> column) {
		for(String key : column) {

			String value = null;
			if(addrRowKeys.contains(key)) {
				value = addrRowEXT.getValue(key, jsonObject, addrRowLevels, addrRow);
			}
			else {
				value = featureEXT.getValue(key, jsonObject);
			}
			
			if(value != null) {
				return value;
			}
		}
		return null;
	}
	
}
