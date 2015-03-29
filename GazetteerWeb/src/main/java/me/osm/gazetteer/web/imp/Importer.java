package me.osm.gazetteer.web.imp;

import static me.osm.gazetteer.web.imp.IndexHolder.LOCATION;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import me.osm.gazetteer.web.ESNodeHodel;
import me.osm.gazetteer.web.FeatureTypes;
import me.osm.gazetteer.web.executions.AbortedException;
import me.osm.gazetteer.web.executions.BackgroundExecutorFacade.BackgroundExecutableTask;
import me.osm.gazetteer.web.utils.OSMDocSinglton;
import me.osm.gazetteer.web.utils.ReplacersCompiler;
import me.osm.osmdoc.localization.L10n;
import me.osm.osmdoc.model.Feature;
import me.osm.osmdoc.read.OSMDocFacade;

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

public class Importer extends BackgroundExecutableTask {
	
	private static final OSMDocFacade FACADE = OSMDocSinglton.get().getFacade();
	
	protected ObjectsWeightBuilder weighter;

	Logger log = LoggerFactory.getLogger(Importer.class);

	private static final int BATCH_SIZE = 1000;

	private Client client;
	private BulkRequestBuilder bulkRequest;
	private IndexHolder index = new IndexHolder();

	private String filePath;
	
	private long counter = 0;

	private boolean buildingsGeometry;

	private ListenableActionFuture<BulkResponse> curentBulkRequest;
	
	private List<Replacer> replasers = new ArrayList<>(); 
	
	private static class EmptyAddressException extends Exception {
		private static final long serialVersionUID = 8178453133841622471L;
	}

	public Importer(String source, boolean buildingsGeometry) {
		
		this.buildingsGeometry = buildingsGeometry;
		
		this.filePath = source;
		
		weighter = new DefaultWeightBuilder();
		ReplacersCompiler.compile(replasers, new File("config/replacers/hnIndexReplasers"));
	}

	public static InputStream getFileIS(String osmFilePath) throws IOException,
			FileNotFoundException {
		if (osmFilePath.endsWith("gz")) {
			return new GZIPInputStream(new FileInputStream(osmFilePath));
		}
		if (osmFilePath.endsWith("bz2")) {
			return new BZip2CompressorInputStream(new FileInputStream(
					osmFilePath));
		}
		return new FileInputStream(osmFilePath);
	}

	@Override
	public void executeTask() throws AbortedException {
		
		client = ESNodeHodel.getClient();
		bulkRequest = client.prepareBulk();
		
		IndicesExistsResponse response = new IndicesExistsRequestBuilder(
				client.admin().indices()).setIndices("gazetteer").execute()
				.actionGet();

		if (!response.isExists()) {
			index.createIndex(client);
		}

		InputStream fileIS = null;
		try {
			fileIS = getFileIS(filePath);
			BufferedReader reader = new BufferedReader(new InputStreamReader(fileIS, "UTF8"));
			String line = reader.readLine();
			while (line != null) {
				add(line);
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
	
	private void add(String line) throws AbortedException {
		if(line != null) {
			if(bulkRequest == null) {
				bulkRequest = client.prepareBulk();
			}
			
			line = processLine(line);
			
			if (line != null) {
				IndexRequestBuilder ind = indexRequest(line);
				bulkRequest.add(ind.request());
				
				counter++;
			}
			
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

	private IndexRequestBuilder indexRequest(String line) {
		IndexRequestBuilder ind = new IndexRequestBuilder(client)
			.setSource(line).setIndex("gazetteer").setType(LOCATION);
		return ind;
	}

	private void executeBulk() {
		
		if(curentBulkRequest != null) {
			BulkResponse bulkResponse = curentBulkRequest.actionGet();
			if (bulkResponse.hasFailures()) {
				log.error(bulkResponse.buildFailureMessage());
			}
		}

		curentBulkRequest = bulkRequest.execute();
	}

	private String processLine(String line) {
		try {
			JSONObject obj = new JSONObject(line);
			
			if(!buildingsGeometry) {
				obj = filterFullGeometry(obj);
			}
			
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
			}
			
			obj.remove("alt_addresses");
			obj.remove("alt_addresses_trans");
			
			if(obj.has("housenumber")) {
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
		Set<String> result = new HashSet<>(); 
		for(Replacer replacer : replasers) {
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
		
		shortText = StringUtils.remove(shortText, ",");
		shortText = StringUtils.replace(shortText, "-", " ");
		
		return shortText;
	}

	private String getSearchText(JSONObject obj) throws EmptyAddressException {
		
		StringBuilder sb = new StringBuilder();
		JSONObject addrobj = obj.optJSONObject("address");
		
		if(addrobj != null) {
			String addrText = addrobj.optString("longText");
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
		
		JSONArray poiClasses = obj.getJSONArray("poi_class");
		
		List<Feature> classes = new ArrayList<Feature>();
		for(int i = 0; i < poiClasses.length(); i++) {
			String classCode = poiClasses.getString(i);
			classes.add(FACADE.getFeature(classCode));
		}
		
		for(Feature f : classes) {
			for(String ln : L10n.supported) {
				String translatedTitle = FACADE.getTranslatedTitle(f, Locale.forLanguageTag(ln));
				result.add(translatedTitle);
			}
		}
		
		return result;
	}

	private JSONObject filterFullGeometry(JSONObject jsonObject) {
		
		if(jsonObject.getString("type").equals(FeatureTypes.ADDR_POINT_FTYPE) || 
				jsonObject.getString("type").equals(FeatureTypes.POI_FTYPE)) {
			jsonObject.remove("full_geometry");
		}
		
		return jsonObject;
	}
	
	public List<Replacer> getReplacers() {
		return replasers;
	}

}
