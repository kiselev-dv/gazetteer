package me.osm.gazetter.join.out_handlers;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.output.NullWriter;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.striper.JSONFeature;

/**
 * Allow to pass line handler into external libraries
 * 
 * <b>IMPORTANT</b> this feature is experimental, and
 * work only with ExternalSorting lib.
 *  
 * */
public final class HgnetMergerFakeWriter extends BufferedWriter {

	private List<String> batch = new ArrayList<>();
	private String lastH = null;
	private GazetteerOutWriter gazetteerOutWriter;
	
	/**
	 * @param gazetteerOutWriter
	 */
	public HgnetMergerFakeWriter(GazetteerOutWriter gazetteerOutWriter) {
		super(new NullWriter());
		this.gazetteerOutWriter = gazetteerOutWriter;
	}

	private void mergeBatch(List<String> netBatch) {
		
		JSONFeature baseFeature = null;
		LinkedHashSet<String> members = new LinkedHashSet<>(100);
		LinkedHashSet<JSONObject> geometries = new LinkedHashSet<>(100);
		LinkedHashSet<String> geometryIds = new LinkedHashSet<>(100);
		
		Collections.sort(netBatch);
		
		List<JSONFeature> batch = new ArrayList<>(netBatch.size());
		for(String s : netBatch) {
			batch.add(new JSONFeature(s));
		}
		
		for(JSONFeature obj : batch) {
			if(baseFeature == null) {
				baseFeature = obj;
			}
			
			for(int i = 0; i < obj.getJSONArray("members").length(); i++) {
				members.add(obj.getJSONArray("members").getString(i));
			}
			
			if(geometries != null) {
				for(int i = 0; i < obj.getJSONArray("geometries").length(); i++) {
					JSONObject g = obj.getJSONArray("geometries").getJSONObject(i);
					if(g != null && geometryIds.add(g.getString("id"))) {
						geometries.add(g);
					}
				}
			}
		}
		
		baseFeature.put("members", members);
		baseFeature.put("geometries", geometries);
		
		/* TODO: Проблема, даже если дороги не соединены, но находятся в пределах
		 * одного набора границ, и имеют одинаковые имена - они будут склеены.
		 * 
		 * Можно на этапе джоина, когда мы создаем треды, доставать эндпоинты
		 * и добавлять построенный на них хеш к айдишке сети.
		 * 
		 * Но я побаиваюсь накосячить с таким подходом и выкинуть лишнего.
		 */
		
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
				if(lastH.equals(htag) || batch.isEmpty()) {
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