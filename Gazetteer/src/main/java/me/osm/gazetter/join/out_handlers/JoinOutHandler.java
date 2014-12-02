package me.osm.gazetter.join.out_handlers;

import java.util.List;

import org.json.JSONObject;

public interface JoinOutHandler {

	public JoinOutHandler newInstance(List<String> options);
	
	/**
	 * WARNING: This method is not thread safe. 
	 * If you store some data from this method
	 * to class fields, use synchronization
	 * */
	public void handle(JSONObject object, String stripe);

	public void stripeDone(String stripe);
	public void allDone();

}
