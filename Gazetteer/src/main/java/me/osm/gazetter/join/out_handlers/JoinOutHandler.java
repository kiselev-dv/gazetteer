package me.osm.gazetter.join.out_handlers;

import java.util.List;

import org.json.JSONObject;

public interface JoinOutHandler {

	public JoinOutHandler newInstance(List<String> options);
	
	/** You'll get link to shared object, not a copy. */
	public void handle(JSONObject object, String stripe);
	public void stripeDone(String stripe);
	public void allDone();

}
