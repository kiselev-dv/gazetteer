package me.osm.gazetteer.web;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.io.IOUtils;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequestBuilder;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.client.AdminClient;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Importer {
	
	Logger log = LoggerFactory.getLogger(Importer.class);

	private static final int BATCH_SIZE = 10000;

	private Client client;
	private BulkRequestBuilder bulkRequest;

	private String filePath;
	
	private long counter = 0;

	public Importer(String filePath) {
		this.filePath = filePath;
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

	public void createIndex() {

		AdminClient admin = client.admin();

		String source;
		try {
			source = IOUtils.toString(getClass().getResourceAsStream(
					"gazetteer.json"));
		} catch (IOException e) {
			throw new RuntimeException("couldn't read index settings", e);
		}
		Settings settings = ImmutableSettings.builder().loadFromSource(source)
				.build();

		admin.indices().create(new CreateIndexRequest("gazetteer", settings))
				.actionGet();

	}

	public void run() {

		try {
			IndicesExistsResponse response = new IndicesExistsRequestBuilder(
					client.admin().indices()).setIndices("gazetteer").execute()
					.actionGet();

			if (!response.isExists()) {
				createIndex();
			}

			InputStream fileIS = null;
			try {
				fileIS = getFileIS(filePath);
				BufferedReader reader = new BufferedReader(new InputStreamReader(fileIS));
				String line = reader.readLine();
				while (line != null) {
					add(line);
					line = reader.readLine();
				}
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
			finally {
				IOUtils.closeQuietly(fileIS);
			}
			
			
		} finally {
			close();
		}
	}
	
	private void add(String line) {
		if(bulkRequest == null) {
			bulkRequest = client.prepareBulk();
		}
		
		IndexRequestBuilder ind = new IndexRequestBuilder(client)
			.setSource(line).setIndex("gazetteer").setType("place");
		bulkRequest.add(ind.request());
		
		counter++;
		
		if(counter % BATCH_SIZE == 0) {
			BulkResponse bulkResponse = bulkRequest.execute().actionGet();
			if (bulkResponse.hasFailures()) {
				throw new RuntimeException(bulkResponse.buildFailureMessage());
			}
			
			log.info("{} rows imported", counter);
			
			bulkRequest = client.prepareBulk();
		}
	}

	private void close() {
		if (bulkRequest.numberOfActions() > 0) {
			BulkResponse bulkResponse = bulkRequest.execute().actionGet();

			if (bulkResponse.hasFailures()) {
				throw new RuntimeException(bulkResponse.buildFailureMessage());
			}
			log.info("Import done. {} rows imported.", counter);
		}
	}

}
