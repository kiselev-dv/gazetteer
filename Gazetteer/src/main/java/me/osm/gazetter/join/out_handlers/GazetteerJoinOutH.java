package me.osm.gazetter.join.out_handlers;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;

import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.utils.FileUtils;

import org.json.JSONObject;

public class GazetteerJoinOutH implements JoinOutHandler {
	
	public static final String NAME = "out-json";

	private PrintWriter writer = new PrintWriter(System.out);
	
	@Override
	public JoinOutHandler newInstance(List<String> options) {
		try {
			if(!options.isEmpty()) {
				writer = FileUtils.getPrintwriter(new File(options.get(0)), false);
			}
		}
		catch(Exception e) {
			throw new RuntimeException(e);
		}
		
		return this;
	}

	@Override
	public void handle(JSONObject object, String stripe) {
		//writer.println(object.optString("type"));
	}

	@Override
	public void stripeDone(String stripe) {
		writer.flush();
	}

	@Override
	public void allDone() {
		writer.flush();
		writer.close();
	}

}
