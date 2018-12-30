package me.osm.gazetteer.out;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicInteger;

import me.osm.gazetteer.join.out_handlers.AddressPerRowJOHBase;
import me.osm.gazetteer.join.out_handlers.HandlerOptions;
import me.osm.gazetteer.join.out_handlers.JoinOutHandler;
import me.osm.gazetteer.striper.FeatureTypes;
import me.osm.osmdoc.read.DOCFileReader;
import me.osm.osmdoc.read.DOCFolderReader;
import me.osm.osmdoc.read.DOCReader;
import me.osm.osmdoc.read.OSMDocFacade;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.supercsv.io.CsvListWriter;
import org.supercsv.prefs.CsvPreference;

import com.google.code.externalsorting.ExternalSort;

public class CSVOutWriter extends AddressPerRowJOHBase {

	public static final String NAME = "out-csv";

	public static Comparator<String> defaultcomparator;

	private List<List<String>> columns;

	private FeatureValueExtractor featureEXT = new FeatureValueExctractorImpl();
	private FeatureValueExtractor poiEXT;
	private AddrRowValueExtractor addrRowEXT = new AddrRowValueExctractorImpl();

	private Set<String> addrRowKeys = new HashSet<String>(addrRowEXT.getSupportedKeys());
	private Set<String> allSupportedKeys = new HashSet<String>(featureEXT.getSupportedKeys());

	private OSMDocFacade osmDocFacade;
	private DOCReader reader;

	private int uuidColumnIndex = -1;

	private static final Set<String> OPTIONS = new HashSet<String>(
			Arrays.asList("out", "columns", "types", "poi-catalog", "header"));

	private CsvListWriter csvWriter = null;

	private LinkedHashSet<String> orderedTypes;

	private String outFile;
	private static AtomicInteger instances = new AtomicInteger();

	private String tmpFile;

	private List<String> header = null;

	@Override
	public JoinOutHandler initialize(HandlerOptions parsedOpts) {

		if(parsedOpts.has("usage") || parsedOpts.has("help")) {
			printUsage();
			System.exit(0);
		}

		if(parsedOpts.has(null)) {
			initializeWriter(parsedOpts.getString(null, null));
		}
		else {
			initializeWriter(parsedOpts.getString("out", null));
		}

		String columnsString = StringUtils.join(parsedOpts.getList("columns", null), " ");
		if (columnsString == null) {
			System.err.println("There are no columns provided.");
			System.err.println("\tUse columns=column1,column2,column3");
			System.err.println("\tSee https://github.com/kiselev-dv/gazetteer/wiki/csv-output for more info.");

			System.exit(2);
		}
		this.columns = parseColumns(columnsString);
		this.header = parsedOpts.getList("header", null);

		allSupportedKeys.addAll(addrRowKeys);

		checkColumnsKeys();

		createComparator();

		orderedTypes = new LinkedHashSet<>(parsedOpts.getList("types", Arrays.asList("adrpnt", "poipnt")));

		initializePOICatalog(parsedOpts);

		return this;
	}

	private void printUsage() {
		StringBuilder usage = new StringBuilder();
		usage.append("Usage: join --handlers ").append(NAME).append("[<out_file>|out=<out_file>]");

		int i = 0;
		for(String opt : OPTIONS) {
			usage.append(" ").append("[").append(opt).append("[=<val>]]");
			if(i%3 == 0 && i > 0) {
				usage.append("\n\t");
			}
			i++;
		}

		System.out.println(usage.toString());
	}

	@Override
	protected Collection<String> getHandlerArguments(
			Collection<String> defOptions) {

		defOptions.addAll(OPTIONS);

		return defOptions;
	}

	@Override
	protected void initializeWriter(String file) {
		this.outFile = file;
		tmpFile = "out-csv" + instances.getAndIncrement() + ".csv.tmp";
		super.initializeWriter(tmpFile);
		csvWriter = new CsvListWriter(writer, CsvPreference.TAB_PREFERENCE);
	}

	private void initializePOICatalog(HandlerOptions paresedOpts) {
		String poiCatalog = paresedOpts.getString("poi-catalog", "jar");
		if(poiCatalog.endsWith(".xml") || poiCatalog.equals("jar")) {
			reader = new DOCFileReader(poiCatalog);
		}
		else {
			reader = new DOCFolderReader(poiCatalog);
		}

		osmDocFacade = new OSMDocFacade(reader, null);

		poiEXT = new PoiValueExctractorImpl(osmDocFacade);
	}

	private void createComparator() {
		int i = 0;
		for(List<String> bc : this.columns) {
			for(String key : bc) {
				if(key.equals("uid")) {
					uuidColumnIndex = i;
				}
			}
			i++;
		}

		if(this.uuidColumnIndex < 0) {
			defaultcomparator = new Comparator<String>() {
				@Override
				public int compare(String r1, String r2) {
					if(r1 == null && r2 == null) return 0;
	            	if(r1 == null || r2 == null) return r1 == null ? -1 : 1;

	            	return r1.compareTo(r2);
				}
			};
		}
		else {
			defaultcomparator = new Comparator<String>() {
				@Override
				public int compare(String r1, String r2) {
					if(r1 == null && r2 == null) return 0;
	            	if(r1 == null || r2 == null) return r1 == null ? -1 : 1;

					String uid1 = StringUtils.split(r1, '\t')[uuidColumnIndex];
					String uid2 = StringUtils.split(r2, '\t')[uuidColumnIndex];

					return uid1.compareTo(uid2);
				}
			};
		}
	}

	private void checkColumnsKeys() {
		boolean flag = false;
		for(List<String> c : this.columns) {
			for(String key : c) {
				if(featureEXT != null && !featureEXT.supports(key)
						&& poiEXT != null && !poiEXT.supports(key)
						&& addrRowEXT != null && !addrRowEXT.supports(key)) {
					System.err.println("Column key " + key + " is not supported.");
					flag = true;
				}
			}
		}

		if(flag) {
			System.exit(1);
		}
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

	@Override
	public void handle(JSONObject object, JSONObject address, String stripe) {

		String ftype = object.getString("ftype");

		if(orderedTypes.contains(ftype) && address != null) {

			if(FeatureTypes.ADMIN_BOUNDARY_FTYPE.equals(ftype) && !StringUtils.contains(stripe, "binx")) {
				return;
			}

			List<Object> row = new ArrayList<>();
			Map<String, JSONObject> mapLevels = mapLevels(address);

			for (List<String> column : columns) {
				row.add(getColumn(ftype, object, mapLevels, address, column));
			}

			writeCsvRow(row);
		}

	}

	private synchronized void writeCsvRow(List<Object> row) {
		try {
			csvWriter.write(row);
			csvWriter.flush();
		} catch (IOException e) {
			throw new RuntimeException(e);
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

	@Override
	public void allDone() {

		try {
			csvWriter.flush();
			super.allDone();

			List<File> batch = ExternalSort.sortInBatch(
					new File(tmpFile), defaultcomparator, ExternalSort.DEFAULTMAXTEMPFILES,
					Charset.forName("utf8"), null, true);

			ExternalSort.mergeSortedFiles(batch, new File(outFile), defaultcomparator, Charset.forName("utf-8"), true,
                    false, false, getHeaderString());

			new File(tmpFile).delete();

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private String getHeaderString() {
		if(this.header == null) {
			return null;
		}

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			CsvListWriter csvListWriter = new CsvListWriter(new OutputStreamWriter(baos), CsvPreference.TAB_PREFERENCE);
			csvListWriter.write(this.header);
			csvListWriter.flush();
			csvListWriter.close();

			return baos.toString("utf-8");
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

}
