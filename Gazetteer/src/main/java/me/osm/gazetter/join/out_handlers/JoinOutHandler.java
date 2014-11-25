package me.osm.gazetter.join.out_handlers;

import org.json.JSONObject;

public interface JoinOutHandler {

	public JoinOutHandler newInstacne();
	
	public void handle(JSONObject object, String stripe);
	public void stripeDone(String stripe);
	public void allDone();

}
