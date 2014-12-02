package me.osm.gazetter.join.out_handlers;

import org.json.JSONObject;

public class PrintJoinOutHandler extends SingleWriterJOHBase {
	
	public static final String NAME = "out-print";

	@Override
	public void handle(JSONObject object, String stripe) {
		println(object.toString());
	}


}
