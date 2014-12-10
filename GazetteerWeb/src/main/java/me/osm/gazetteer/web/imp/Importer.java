package me.osm.gazetteer.web.imp;

import static me.osm.gazetteer.web.imp.IndexHolder.LOCATION;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

import me.osm.gazetteer.web.ESNodeHodel;
import me.osm.gazetteer.web.FeatureTypes;
import me.osm.gazetteer.web.utils.OSMDocSinglton;
import me.osm.osmdoc.localization.L10n;
import me.osm.osmdoc.model.Feature;
import me.osm.osmdoc.read.OSMDocFacade;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequestBuilder;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.client.Client;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Importer implements Runnable {
	
	private static final OSMDocFacade FACADE = OSMDocSinglton.get().getFacade();

	Logger log = LoggerFactory.getLogger(Importer.class);

	private static final int BATCH_SIZE = 10000;

	private Client client;
	private BulkRequestBuilder bulkRequest;
	private IndexHolder index = new IndexHolder();

	private String filePath;
	
	private long counter = 0;

	private boolean buildingsGeometry;
	
	private static class EmptyAddressException extends Exception {

		private static final long serialVersionUID = 8178453133841622471L;
		
	}

	public Importer(String filePath) {
		this.filePath = filePath;
		client = ESNodeHodel.getClient();
		bulkRequest = client.prepareBulk();
	}

	public Importer(String source, boolean buildingsGeometry) {
		
		this.buildingsGeometry = buildingsGeometry;
		
		this.filePath = source;
		client = ESNodeHodel.getClient();
		bulkRequest = client.prepareBulk();
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
	public void run() {

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
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		finally {
			IOUtils.closeQuietly(fileIS);
		}
	}
	
	private void add(String line) {
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
				
				log.info("{} rows imported", counter);
				
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
		BulkResponse bulkResponse = bulkRequest.execute().actionGet();
		if (bulkResponse.hasFailures()) {
			log.error(bulkResponse.buildFailureMessage());
		}
	}

	private String processLine(String line) {
		JSONObject obj = new JSONObject(line);
		
		if(!buildingsGeometry) {
			obj = filterFullGeometry(obj);
		}
		
		String shortText;
		try {
			shortText = getShortText(obj);
			obj.put("search", shortText);
		} catch (EmptyAddressException e) {
			return null;
		}
		
		obj.remove("alt_addresses");
		obj.remove("alt_addresses_trans");
		
		return obj.toString();
	}

	private String getShortText(JSONObject obj) throws EmptyAddressException {
		
		StringBuilder sb = new StringBuilder();
		JSONObject addrobj = obj.optJSONObject("address");
		
		if(addrobj != null) {
			String addrText = addrobj.optString("text");
			if(StringUtils.isNotBlank(addrText)) {
				sb.append(addrText);
			}
			else {
				throw new EmptyAddressException();
			}
		}
		
		if("poipnt".equals(obj.optString("type"))) {
			JSONArray poiClasses = obj.getJSONArray("poi_class");
			
			List<Feature> classes = new ArrayList<Feature>();
			for(int i = 0; i < poiClasses.length(); i++) {
				String classCode = poiClasses.getString(i);
				classes.add(FACADE.getFeature(classCode));
			}
			
			for(Feature f : classes) {
				for(String ln : L10n.supported) {
					String translatedTitle = FACADE.getTranslatedTitle(f, Locale.forLanguageTag(ln));
					
					sb.append(" ").append(translatedTitle);
				}
			}
			
			String name = obj.optString("name");
			sb.append(" ").append(name);
		}
		
		return StringUtils.remove(sb.toString(), ',');
	}

	private JSONObject filterFullGeometry(JSONObject jsonObject) {
		
		if(jsonObject.getString("type").equals(FeatureTypes.ADDR_POINT_FTYPE) || 
				jsonObject.getString("type").equals(FeatureTypes.POI_FTYPE)) {
			jsonObject.remove("full_geometry");
		}
		
		return jsonObject;
	}

}
