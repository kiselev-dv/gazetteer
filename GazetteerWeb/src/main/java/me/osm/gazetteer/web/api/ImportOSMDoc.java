package me.osm.gazetteer.web.api;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import me.osm.gazetteer.web.ESNodeHodel;
import me.osm.gazetteer.web.imp.IndexHolder;
import me.osm.gazetteer.web.utils.FileUtils;
import me.osm.gazetteer.web.utils.FileUtils.LineHandler;
import me.osm.gazetteer.web.utils.OSMDocSinglton;
import me.osm.osmdoc.localization.L10n;
import me.osm.osmdoc.model.Feature;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.restexpress.Request;
import org.restexpress.Response;

public class ImportOSMDoc {
	
	public JSONObject read(Request request, Response response){
		
		String source = request.getHeader("source");
		
		return run(source);
	}

	public JSONObject run(String source) {
		JSONObject result = new JSONObject();
		
		final BulkRequestBuilder bulk = ESNodeHodel.getClient().prepareBulk();

		List<JSONObject> features = OSMDocSinglton.get().getFacade().listTranslatedFeatures(null);
		for(JSONObject obj : features) {
			
			IndexRequestBuilder ind = new IndexRequestBuilder(ESNodeHodel.getClient())
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
						
						IndexRequestBuilder ind = new IndexRequestBuilder(ESNodeHodel.getClient())
						.setSource(obj.toString()).setIndex("gazetteer").setType(IndexHolder.POI_CLASS);
						bulk.add(ind.request());
						
					}
				}
			
				
			});
			
		}
		
		bulk.execute().actionGet();
		
		result.put("result", "success");
		
		return result;
	}
}
