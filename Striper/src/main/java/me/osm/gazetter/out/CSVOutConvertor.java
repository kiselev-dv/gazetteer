package me.osm.gazetter.out;

import java.io.IOException;
import java.io.OutputStreamWriter;

import me.osm.gazetter.striper.Constants;

import org.json.JSONArray;
import org.json.JSONObject;

import au.com.bytecode.opencsv.CSVWriter;

public class CSVOutConvertor implements OutConverter {

	private CSVWriter writer;
	
	public CSVOutConvertor() {
		writer = new CSVWriter(new OutputStreamWriter(System.out));
	}
	
	
	@Override
	public void handle(String s) {
		JSONObject json = new JSONObject(s);
		
		/* TODO: For now handle only addr points,
		 * later rewrite output for particular types
		 * of objects.
		 */
		if(Constants.ADDR_POINT_FTYPE.equals(json.optString("ftype"))) {
			JSONArray coords = json.getJSONObject("geometry").getJSONArray("coordinates");
			double lon = coords.getDouble(0);
			double lat = coords.getDouble(1);
			
			JSONArray addrArray = json.getJSONArray("addresses");

			for(int i = 0; i < addrArray.length(); i++) {
				String[] row = getArray(lon, lat, addrArray.getJSONObject(i));
				writer.writeNext(row);
			}
			
		}

	}

	private String[] getArray(double lon, double lat, JSONObject addrObj) {
		//13 addr levels and 2 coords
		String[] result = new String[15];
		
		result[0] = String.valueOf(lon);
		result[1] = String.valueOf(lat);

		JSONArray parts = addrObj.getJSONArray("parts");
		for(int i = 0;i < parts.length(); i++) {
			
			JSONObject addrLVL = parts.getJSONObject(i);
			
			String name = addrLVL.getString("name");
			int lvl = addrLVL.getInt("lvl");
			
			int csvIndex = lvl / 10 - 1 + 2;
			
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
