package me.osm.gazetteer.web.imp;

import static me.osm.gazetteer.web.imp.IndexHolder.LOCATION;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import me.osm.gazetteer.web.ESNodeHolder;
import me.osm.gazetteer.web.FeatureTypes;
import me.osm.gazetteer.web.GazetteerWeb;
import me.osm.gazetteer.web.executions.AbortedException;
import me.osm.gazetteer.web.executions.BackgroudTaskDescription;
import me.osm.gazetteer.web.executions.BackgroundExecutorFacade.BackgroundExecutableTask;
import me.osm.gazetteer.web.utils.OSMDocSinglton;
import me.osm.gazetteer.web.utils.ReplacersCompiler;
import me.osm.osmdoc.localization.L10n;
import me.osm.osmdoc.model.Feature;
import me.osm.osmdoc.model.Tag.Val;
import me.osm.osmdoc.read.OSMDocFacade;
import me.osm.osmdoc.read.tagvalueparsers.LogTagsStatisticCollector;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.elasticsearch.action.ListenableActionFuture;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequestBuilder;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.client.Client;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.operation.linemerge.LineMerger;

public class LocationsDumpImporter extends BackgroundExecutableTask {
	
	private static final LogTagsStatisticCollector POI_STATISTICS = new LogTagsStatisticCollector();

	private static final OSMDocFacade FACADE = OSMDocSinglton.get().getFacade();
	
	protected ObjectsWeightBuilder weighter;

	Logger log = LoggerFactory.getLogger(LocationsDumpImporter.class);

	protected static final int BATCH_SIZE = 1000;

	protected Client client;
	protected BulkRequestBuilder bulkRequest;

	private String filePath;
	
	protected long counter = 0;

	private boolean buildingsGeometry;

	private ListenableActionFuture<BulkResponse> curentBulkRequest;
	
	private List<Replacer> hnReplacers = new ArrayList<>(); 
	private List<Replacer> streetsReplacers = new ArrayList<>(); 
	
	private static final GeometryFactory factory = new GeometryFactory();
	
	private Transliterator transliterator = null;
	
	private Set<String> skip;
	
	private String callback;
	
	public void setCallback(String callback) {
		this.callback = callback;
	};
	
	@Override
	public String getCallbackURL() {
		return callback;
	}
	
	private static class EmptyAddressException extends Exception {
		private static final long serialVersionUID = 8178453133841622471L;
	}

	public LocationsDumpImporter(String source, boolean buildingsGeometry) {
		
		String trClass = GazetteerWeb.config().getTransliteratorClass();
		try {
			this.transliterator = (Transliterator) Class.forName(trClass).newInstance();
		}
		catch (Exception e) {
			log.warn("Couldn't initialize transliterator {}", trClass);
			this.transliterator = new ApacheASCIIFoldTransliterator();
		}
		
		this.buildingsGeometry = buildingsGeometry;
		
		this.filePath = source;
		
		weighter = new DefaultWeightBuilder();
		ReplacersCompiler.compile(hnReplacers, new File("config/replacers/index/hnIndexReplasers"));
		ReplacersCompiler.compile(streetsReplacers, new File("config/replacers/index/streetsReplacers"));
		
		this.skip = new HashSet<>(GazetteerWeb.config().getImportSkipTypes());
	}

	public static InputStream getFileIS(String osmFilePath) throws IOException,
			FileNotFoundException {
		
		InputStream fileIS = null;
		
		if(osmFilePath.startsWith("http")) {
			fileIS = new URL(osmFilePath).openStream();
		}
		else {
			fileIS = new FileInputStream(osmFilePath);
		}
		
		if (osmFilePath.endsWith("gz")) {
			return new GZIPInputStream(fileIS);
		}
		if (osmFilePath.endsWith("bz2")) {
			return new BZip2CompressorInputStream(fileIS);
		}
		
		return fileIS;
	}

	@Override
	public void executeTask() throws AbortedException {
		
		client = ESNodeHolder.getClient();
		bulkRequest = client.prepareBulk();
		
		IndicesExistsResponse response = new IndicesExistsRequestBuilder(
				client.admin().indices()).setIndices("gazetteer").execute()
				.actionGet();

		if (!response.isExists()) {
			IndexHolder.createIndex();
		}

		InputStream fileIS = null;
		try {
			fileIS = getFileIS(filePath);
			BufferedReader reader = new BufferedReader(new InputStreamReader(fileIS, "UTF8"));
			String line = reader.readLine();
			while (line != null) {
				addRequestToBatch(line);
				line = reader.readLine();
			}
			
			if(bulkRequest.numberOfActions() > 0) {
				executeBulk();
			}
			
			log.info("Import done. {} rows imported.", counter);
		}
		catch (AbortedException aborted) {
			log.info("Import was interrupted. {} rows imported.", counter);
			throw aborted;
		}
		catch (Exception e) {
			throw new AbortedException("Import aborted. Root error msg: " + ExceptionUtils.getRootCauseMessage(e), e, false);
		}
		finally {
			IOUtils.closeQuietly(fileIS);
		}
	}
	
	protected void addRequestToBatch(String line) throws AbortedException {
		if(line != null) {
			if(bulkRequest == null) {
				bulkRequest = client.prepareBulk();
			}
			
			createRequestAndAdd(line);
			
			if(counter % BATCH_SIZE == 0) {
				executeBulk();
				
				if(isAborted()) {
					throw new AbortedException(null, null, true);
				}
				
				log.info("{} rows imported", 
						NumberFormat.getNumberInstance().format(counter));
				
				bulkRequest = client.prepareBulk();
			}
		}
	}

	protected void createRequestAndAdd(String line) {
		line = processLine(line);
		
		if (line != null) {
			IndexRequestBuilder ind = indexRequest(line);
			bulkRequest.add(ind.request());
			
			counter++;
		}
	}

	protected IndexRequestBuilder indexRequest(String line) {
		IndexRequestBuilder ind = new IndexRequestBuilder(client)
			.setSource(line).setIndex("gazetteer").setType(LOCATION);
		return ind;
	}

	protected void executeBulk() {
		
		if(curentBulkRequest != null && !curentBulkRequest.isDone()) {
			BulkResponse bulkResponse = curentBulkRequest.actionGet();
			if (bulkResponse.hasFailures()) {
				log.error(bulkResponse.buildFailureMessage());
			}
		}
		
		if(bulkRequest.numberOfActions() > 0) {
			curentBulkRequest = bulkRequest.execute();
		}
	}

	protected String processLine(String line) {
		try {
			
			JSONObject obj = new JSONObject(line);

			if(doSkip(obj)) {
				return null;
			}
			
			if(!buildingsGeometry) {
				obj = filterFullGeometry(obj);
			}
			
			obj = mergeHighwayNetsGeometry(obj);
			
			filterAddrPartsNames(obj);
			
			try {
				String searchText = getSearchText(obj);
				searchText = sanitizeSearchText(searchText);
				obj.put("search", searchText);
				
			} catch (EmptyAddressException e) {
				return null;
			}
			
			if("poipnt".equals(obj.optString("type"))) {
				obj.put("poi_class_trans", new JSONArray(getPoiTypesTranslated(obj)));
				
				List<Feature> poiClassess = listPoiClassesOSMDoc(obj);
				Map<String, List<Val>> moreTagsVals = new HashMap<String, List<Val>>();
				JSONObject moreTags = FACADE.parseMoreTags(poiClassess, obj.getJSONObject("tags"), 
						POI_STATISTICS, moreTagsVals);
				
				obj.put("more_tags", moreTags);
				
				LinkedHashSet<String> keywords = new LinkedHashSet<String>();
				FACADE.collectKeywords(poiClassess, moreTagsVals, keywords, null);
				
				obj.put("poi_keywords", new JSONArray(keywords));
			}
			
			obj.remove("alt_addresses");
			obj.remove("alt_addresses_trans");
			obj.remove("hhash");
			
			if(obj.has("housenumber")) {
				obj.put("housenumber_exact", obj.optString("housenumber").toLowerCase());
				obj.put("housenumber_main", getMainHousenumber(obj.optString("housenumber")));
				obj.put("housenumber", 
						new JSONArray(fuzzyHousenumberIndex(obj.optString("housenumber"))));
			}
			
			obj.put("weight", weighter.weight(obj));

			return obj.toString();
		}
		catch (JSONException e) {
			log.error("Failed to parse: " + line);
			return null;
		}
		
	}

	private String getMainHousenumber(String optString) {
		String lowerCase = optString.toLowerCase().trim();
		
		int l = 0;
		for(char c : lowerCase.toCharArray()) {
			if(c < '0' || c > '9') {
				break;
			}
			
			l++;
		}
		
		return lowerCase.substring(0, l);
	}

	private JSONObject mergeHighwayNetsGeometry(JSONObject jsonObject) {
		
		if(jsonObject.getString("type").equals(FeatureTypes.HIGHWAY_NET_FEATURE_TYPE)) {
			JSONArray geometriesArray = jsonObject.optJSONArray("geometries");
			
			if(geometriesArray != null) {
				List<LineString> lss = new ArrayList<>();
				for(int i = 0; i < geometriesArray.length(); i++ ) {
					JSONObject geom = geometriesArray.getJSONObject(i);
					lss.add(getLineStringGeometry(geom.getJSONArray("coordinates")));
				}
				
				LineMerger merger = new LineMerger();
				merger.add(lss);
				
				@SuppressWarnings("unchecked")
				Collection<LineString> merged = (Collection<LineString>)merger.getMergedLineStrings();
				
				jsonObject.remove("geometries");
				jsonObject.getJSONObject("center_point").remove("type");
				jsonObject.put("full_geometry", writeMultiLineString(merged));
			}
			
		}
		
		return jsonObject;
	}

	private boolean doSkip(JSONObject obj) {
		
		if(this.skip.contains(obj.getString("type"))) {
			return true;
		}
		
		return false;
	}

	private void filterAddrPartsNames(JSONObject obj) {
		JSONObject addr = obj.optJSONObject("address");
		if(addr != null) {
			JSONArray parts = addr.optJSONArray("parts");
			
			if(parts != null) {
				for(int i=0; i < parts.length();i++) {
					JSONObject part = parts.getJSONObject(i);
					if(part != null) {
						part.remove("names");
					}
				}
			}
		}
	}

	public List<String> fuzzyHousenumberIndex(String optString) {
		LinkedHashSet<String> result = new LinkedHashSet<>();

		if(StringUtils.isNotBlank(optString)) {
			result.add(optString);
			result.addAll(transformHousenumbers(optString));
		}
		
		return new ArrayList<>(result);
	}

	private Collection<String> transformHousenumbers(String optString) {
		return transform(optString, hnReplacers);
	}

	private Collection<String> transformStreets(String optString) {
		Collection<String> s = transform(optString, streetsReplacers);
		s.add(optString);
		return s;
	}
	
	private Collection<String> transform(String optString, Collection<Replacer> replacers) {
		Set<String> result = new HashSet<>(); 
		for(Replacer replacer : replacers) {
			try {
				Collection<String> replace = replacer.replace(optString);
				if(replace != null) {
					for(String s : replace) {
						if(StringUtils.isNotBlank(s) && !"null".equals(s)) {
							result.add(s);
						}
					}
				}
			}
			catch (Exception e) {
				
			}
		}
		
		return result;
	}

	private String sanitizeSearchText(String shortText) {
		
		shortText = shortText.toLowerCase();
		shortText = StringUtils.remove(shortText, ",");
		shortText = StringUtils.replace(shortText, "-", " ");
		
		shortText += addTransliteration(shortText);
		
		return shortText;
	}

	private String addTransliteration(String text) {
		
		StringBuilder sb = new StringBuilder();
		
		for(String term : StringUtils.split(text, GazetteerWeb.config().getQueryAnalyzerSeparators())) {
			String translit = transliterator.transliterate(term);
			if(!term.equals(translit)) {
				sb.append(" ").append(translit);
			}
		}
		
		return sb.toString();
	}

	private String getSearchText(JSONObject obj) throws EmptyAddressException {
		
		StringBuilder sb = new StringBuilder();
		JSONObject addrobj = obj.optJSONObject("address");
		
		if(addrobj != null) {
			
			String addrText = null;
			
			JSONArray jsonArray = addrobj.optJSONArray("parts");
			if(jsonArray == null) {
				addrText = addrobj.optString("longText");
			}
			else {
				LinkedHashMap<String, JSONObject> addrLevels = new LinkedHashMap<>(10);
				for(int i = 0; i < jsonArray.length(); i++ ) {
					JSONObject addrPart = jsonArray.getJSONObject(i);
					addrLevels.put(addrPart.getString("lvl"), addrPart);
				}
				
				addrText = getAddrText(obj, addrobj, addrLevels);
			}
			
			if(!"admbnd".equals(obj.optString("type")) && !"plcpnt".equals(obj.optString("type"))) {
				String streetName  = getStreetName(obj, addrobj);
				if(streetName != null) {
					Collection<String> transformStreets = transformStreets(streetName);
					
					JSONArray streetAltNames = obj.optJSONArray("street_alternate_names");
					if(streetAltNames == null) {
						streetAltNames = new JSONArray();
					}
					for(String alt : transformStreets) {
						obj.put("street_name_var", alt);
					}
				}
			}
			
			if(StringUtils.isNotBlank(addrText)) {
				sb.append(addrText);
				
				if(!obj.has("locality_name") && obj.has("nearest_place")) {
					sb.append(" ").append(obj.getJSONObject("nearest_place").getString("name"));
				}
			}
			else {
				throw new EmptyAddressException();
			}
		}
		
		if("poipnt".equals(obj.optString("type"))) {
			List<String> titles = getPoiTypesTranslated(obj);
			
			for(String s : titles) {
				sb.append(" ").append(s);
			}
			
			String name = obj.optString("name");
			sb.append(" ").append(name);
			
			JSONObject tags = obj.optJSONObject("tags");
			if(tags != null) {
				concatTagValue(sb, tags, "operator");
				concatTagValue(sb, tags, "brand");
				concatTagValue(sb, tags, "network");
				concatTagValue(sb, tags, "ref");
				concatTagValue(sb, tags, "branch");
			}
		}
		
		return StringUtils.remove(sb.toString(), ',');
	}

	private String getStreetName(JSONObject obj, JSONObject addrobj) {
		
		if("hghnet".equals(obj.optString("type")) || "hghway".equals(obj.optString("type")) ) {
			return obj.optString("name");
		}
		
		JSONArray jsonArray = addrobj.optJSONArray("parts");
		if(jsonArray != null) {
			for(int i = 0; i < jsonArray.length(); i++ ) {
				JSONObject addrPart = jsonArray.getJSONObject(i);
				if("street".equals(addrPart.getString("lvl"))) {
					return addrPart.getString("name");
				}
			}
		}
		
		return null;
	}

	private String getAddrText(JSONObject obj, JSONObject addrobj,
			LinkedHashMap<String, JSONObject> addrLevels) {
		
		StringBuilder sb = new StringBuilder();
		
		for(Entry<String, JSONObject> entry : addrLevels.entrySet()) {
			JSONObject part = entry.getValue();
			
			String lvlText = getLvlText(part, entry.getValue());
			if(StringUtils.isNotBlank(lvlText)) {
				sb.append(" ").append(lvlText);
			}
		}
		
		return sb.toString();
	}

	private String getLvlText(JSONObject part, JSONObject value) {
		String name = part.getString("name");
		
		if("street".equals(part.getString("lvl"))) {
			return StringUtils.join(transformStreets(name), " ").toLowerCase();
		}
		
		return name;
	}

	private void concatTagValue(StringBuilder sb, JSONObject tags, String tag) {
		String tv = StringUtils.stripToNull(tags.optString(tag));
		if(tv != null) {
			for(String s : StringUtils.split(tv, ";")) {
				sb.append(" ").append(s);
			}
		}
	}

	private List<String> getPoiTypesTranslated(JSONObject obj) {
		
		List<String> result = new ArrayList<String>(1);
		
		List<Feature> classes = listPoiClassesOSMDoc(obj);
		
		for(Feature f : classes) {
			for(String ln : L10n.supported) {
				String translatedTitle = FACADE.getTranslatedTitle(f, Locale.forLanguageTag(ln));
				result.add(translatedTitle);
			}
		}
		
		return result;
	}

	private List<Feature> listPoiClassesOSMDoc(JSONObject obj) {
		JSONArray poiClasses = obj.getJSONArray("poi_class");
		
		List<Feature> classes = new ArrayList<Feature>();
		for(int i = 0; i < poiClasses.length(); i++) {
			String classCode = poiClasses.getString(i);
			Feature poiClass = FACADE.getFeature(classCode);
			if(poiClass != null) {
				classes.add(poiClass);
			}
			else {
				log.warn("Couldn't find poi class for code {}", classCode);
			}
		}
		return classes;
	}

	
	private JSONObject filterFullGeometry(JSONObject jsonObject) {
		
		if(jsonObject.getString("type").equals(FeatureTypes.ADDR_POINT_FTYPE) || 
				jsonObject.getString("type").equals(FeatureTypes.POI_FTYPE)) {
			jsonObject.remove("full_geometry");
		}
		return jsonObject;
	}
	
	private JSONObject writeMultiLineString(Collection<LineString> merged) {
		JSONObject result = new JSONObject();
		result.put("type", "multilinestring");
		
		JSONArray coords = new JSONArray();
		result.put("coordinates", coords);
		for(LineString ls : merged) {
			coords.put(lineStringCoords(ls));
		}
		
		return result;
	}

	private JSONArray lineStringCoords(LineString ls) {
		
		JSONArray result = new JSONArray();
		
		Coordinate[] coordinates = ls.getCoordinates();
		for(Coordinate c : coordinates) {
			result.put(new double[]{c.x, c.y});
		}
		
		return result;
	}

	public static LineString getLineStringGeometry(JSONArray coordsJSON) {
		Coordinate[] coords = new Coordinate[coordsJSON.length()];
		
		for(int i = 0; i < coordsJSON.length(); i++) {
			JSONArray p = coordsJSON.getJSONArray(i);
			coords[i] = new Coordinate(p.getDouble(0), p.getDouble(1));
		}
		
		return factory.createLineString(coords);
	}
	
	public List<Replacer> getReplacers() {
		return hnReplacers;
	}

	@Override
	public BackgroudTaskDescription description() {
		BackgroudTaskDescription description = new BackgroudTaskDescription();
		
		description.setId(this.getId());
		description.setUuid(this.getUUID());
		
		description.setClassName(getClass().getName());
		Map<String, Object> parameters = new HashMap<String, Object>();
		description.setClassName(getClass().getName());
		description.setParameters(parameters);
		
		parameters.put("source", filePath);
		parameters.put("skip", new HashSet<>(skip));
		parameters.put("callback", callback);
		
		return description;
	}

}
