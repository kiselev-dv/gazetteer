package me.osm.gazetter.join.out_handlers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import me.osm.gazetter.striper.JSONFeature;
import me.osm.gazetter.utils.FileUtils.LineHandler;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public final class HighwaysMerger implements LineHandler {
	
	private List<String> batch = new ArrayList<>();
	private String lastH = null;
	private GazetteerOutWriter gazetteerOutWriter;
	
	public HighwaysMerger(GazetteerOutWriter gazetteerOutWriter) {
		this.gazetteerOutWriter = gazetteerOutWriter;
	}

	@Override
	public void handle(String s) {
		int indexOf = StringUtils.indexOf(s, '\t');
		if(indexOf >= 0) {
			String htag = s.substring(0, indexOf);
			String json = s.substring(indexOf + 1, s.length());
			
			if(lastH == null) {
				lastH = htag;
			}
			else {
				if(lastH.equals(htag)) {
					batch.add(json);
				}
				else if(!batch.isEmpty()) {
					
					if(batch.size() == 1) {
						writeOut(batch.get(0));
					}
					else {
						mergeBatch(batch);
					}
					
					batch.clear();
					batch.add(json);
				}
			}
			
			lastH = htag;
		}
	}
	
	private void mergeBatch(List<String> batch2) {
		
		Iterator<String> iterator = batch2.iterator();
		
		JSONObject baseFeature = new JSONObject(iterator.next());
		
		while(iterator.hasNext()) {
			JSONObject obj2 = new JSONFeature(iterator.next());
			
			JSONArray members = baseFeature.getJSONArray("members");
			for(int i = 0; i < obj2.getJSONArray("members").length(); i++) {
				members.put(obj2.getJSONArray("members").get(i));
			}

			JSONArray geometries = baseFeature.optJSONArray("geometries");
			if(geometries != null) {
				for(int i = 0; i < obj2.getJSONArray("geometries").length(); i++) {
					geometries.put(obj2.getJSONArray("geometries").get(i));
				}
			}

			iterator.remove();
		}
		
		writeOut(baseFeature.toString());
				
	}

	private void writeOut(String string) {
		gazetteerOutWriter.writeMergedHGHNET(string);
	}

	
}