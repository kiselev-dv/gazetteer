package me.osm.gazetteer.web.api;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import me.osm.gazetteer.web.ESNodeHolder;
import me.osm.gazetteer.web.api.meta.Endpoint;
import me.osm.gazetteer.web.api.meta.Parameter;
import me.osm.gazetteer.web.imp.IndexHolder;
import me.osm.gazetteer.web.utils.FileUtils;
import me.osm.gazetteer.web.utils.FileUtils.LineHandler;
import me.osm.gazetteer.web.utils.OSMDocSinglton;
import me.osm.osmdoc.localization.L10n;
import me.osm.osmdoc.model.Feature;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsAction;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequestBuilder;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.delete.DeleteAction;
import org.elasticsearch.action.delete.DeleteRequestBuilder;
import org.elasticsearch.action.index.IndexAction;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.domain.metadata.UriMetadata;

public class ImportOSMDoc implements DocumentedApi {
	
	public JSONObject read(Request request, Response response){
		
		String source = request.getHeader("source");
		boolean drop = "true".equals(request.getHeader("drop"));
		
		return run(source, drop);
	}

	public JSONObject run(String source, boolean drop) {
		JSONObject result = new JSONObject();
		
		if(drop) {
			new DeleteRequestBuilder(ESNodeHolder.getClient(), DeleteAction.INSTANCE, "gazetteer")
				.setType(IndexHolder.POI_CLASS).execute().actionGet();
		}
		
		IndicesExistsResponse response = 
				new IndicesExistsRequestBuilder(ESNodeHolder.getClient(), IndicesExistsAction.INSTANCE)
					.setIndices("gazetteer").execute().actionGet();

		if (!response.isExists()) {
			IndexHolder.createIndex();
		}
		
		final BulkRequestBuilder bulk = ESNodeHolder.getClient().prepareBulk();

		List<JSONObject> features = OSMDocSinglton.get().getFacade().listTranslatedFeatures(null);
		for(JSONObject obj : features) {
			
			IndexRequestBuilder ind = new IndexRequestBuilder(ESNodeHolder.getClient(), IndexAction.INSTANCE)
				.setSource(obj.toString()).setIndex("gazetteer").setType(IndexHolder.POI_CLASS);
			
			bulk.add(ind.request());
		}
		
		if(StringUtils.isNotEmpty(source)) {
			
			FileUtils.handleLines(new File(source), new LineHandler() {
				
				@Override
				public void handle(String s) {
					if(s != null) {
						
						JSONObject obj = new JSONObject(s);
						
						String name = obj.getString("name");
						Feature feature = OSMDocSinglton.get().getFacade().getFeature(name);
						
						JSONArray namesTrans = new JSONArray();
						for(String lt : L10n.supported) {
							String translatedTitle = OSMDocSinglton.get().getFacade()
								.getTranslatedTitle(feature, Locale.forLanguageTag(lt));
							
							namesTrans.put(translatedTitle);
						}
						
						obj.put("name_trans", namesTrans);
						
						Set<String> kwds = new HashSet<String>();
						OSMDocSinglton.get().getFacade().collectKeywords(
								Collections.singleton(feature), null, kwds, null);
						
						obj.put("keywords", new JSONArray(kwds));
						
						IndexRequestBuilder ind = new IndexRequestBuilder(ESNodeHolder.getClient(), IndexAction.INSTANCE)
						.setSource(obj.toString()).setIndex("gazetteer").setType(IndexHolder.POI_CLASS);
						bulk.add(ind.request());
						
					}
				}
				
			});
			
			result.put("result", "success");
		}
		else {
			result.put("message", "Empty source");
			result.put("result", "skip");
		}
		
		if(bulk.numberOfActions() > 0) {
			bulk.execute().actionGet();
		}
		
		
		return result;
	}

	@Override
	public Endpoint getMeta(UriMetadata uriMetadata) {
		Endpoint meta = new Endpoint(uriMetadata.getPattern(), "OSM Doc Import", 
				"Imports POI classification.");
		
		meta.getUrlParameters().add(new Parameter("source", 
				"Path to osmdoc catalog folder or xml. "
			  + "Password protected endpoint."));
		
		return meta;
	}
}
