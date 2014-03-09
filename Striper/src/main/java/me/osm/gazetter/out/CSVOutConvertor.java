package me.osm.gazetter.out;

import java.io.IOException;
import java.io.OutputStreamWriter;

import me.osm.gazetter.striper.FeatureTypes;

import org.json.JSONArray;
import org.json.JSONObject;

import au.com.bytecode.opencsv.CSVWriter;

public class CSVOutConvertor implements OutConverter {

	private CSVWriter writer;
	
	private static final String[] header = new String[]{
		"id",
		"osm-id",
		"lon",
		"lat",
		"housenumber",
		"street",
		"place:quarter",
		"place:neighbourhood",
		"place:suburb",
		"place:allotments",
		"place:locality place:isolated_dwelling place:village place:hamlet place:city",
		"boundary:8",
		"boundary:6",
		"boundary:5",
		"boundary:4",
		"boundary:3",
		"boundary:2"
	};
	
	public CSVOutConvertor() {
		writer = new CSVWriter(new OutputStreamWriter(System.out));
		writer.writeNext(header);
	}
	
	
	@Override
	public void handle(String s) {
		JSONObject json = new JSONObject(s);
		
		/* TODO: For now handle only addr points,
		 * later rewrite output for particular types
		 * of objects.
		 */
		if(FeatureTypes.ADDR_POINT_FTYPE.equals(json.optString("ftype"))) {
			JSONArray coords = json.getJSONObject("geometry").getJSONArray("coordinates");
			double lon = coords.getDouble(0);
			double lat = coords.getDouble(1);
			
			JSONArray addrArray = json.getJSONArray("addresses");
			JSONObject meta = json.getJSONObject("metainfo");

			for(int i = 0; i < addrArray.length(); i++) {
				String[] row = getArray(lon, lat, addrArray.getJSONObject(i), json.getString("id"),  String.valueOf(meta.getString("type").charAt(0)) + meta.getLong("id"));
				writer.writeNext(row);
			}
			
		}

	}

	private String[] getArray(double lon, double lat, JSONObject addrObj, String id, String osmId) {
		//2id + 13 addr levels and 2 coords
		String[] result = new String[header.length];
		
		result[0] = id;
		result[1] = osmId;
		
		result[2] = String.valueOf(lon);
		result[3] = String.valueOf(lat);

		JSONArray parts = addrObj.getJSONArray("parts");
		for(int i = 0;i < parts.length(); i++) {
			
			JSONObject addrLVL = parts.getJSONObject(i);
			
			String name = addrLVL.getString("name");
			int lvl = addrLVL.getInt("lvl");
			
			int csvIndex = lvl / 10 - 1 + 4;
			
			result[csvIndex] = name;
		}

		return result;
	}


	@Override
	public void close() {
		try {
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
