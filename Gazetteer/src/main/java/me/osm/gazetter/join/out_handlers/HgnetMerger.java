package me.osm.gazetter.join.out_handlers;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import me.osm.gazetter.striper.JSONFeature;

import org.apache.commons.io.output.NullWriter;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Allow to pass line handler into external libraries
 * 
 * <b>IMPORTANT</b> this feature is experimental, and
 * work only with ExternalSorting lib.
 *  
 * */
public final class HgnetMerger extends BufferedWriter {

	private List<String> batch = new ArrayList<>();
	private String lastH = null;
	private GazetteerOutWriter gazetteerOutWriter;
	
	/**
	 * @param gazetteerOutWriter
	 */
	public HgnetMerger(GazetteerOutWriter gazetteerOutWriter) {
		super(new NullWriter());
		this.gazetteerOutWriter = gazetteerOutWriter;
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
	
	@Override
	public void write(String str) throws IOException {
		
		int indexOf = StringUtils.indexOf(str, '\t');
		if(indexOf >= 0) {
			String htag = str.substring(0, indexOf);
			String json = str.substring(indexOf + 1, str.length());
			
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

	@Override
	public void newLine() throws IOException {
		
	}

	@Override
	public void flush() throws IOException {

	}

	@Override
	public void close() throws IOException {
		if(!batch.isEmpty()) {
			if(batch.size() == 1) {
				writeOut(batch.get(0));
			}
			else {
				mergeBatch(batch);
			}
		}
	}
}