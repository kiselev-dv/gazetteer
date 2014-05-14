package me.osm.gazetteer.web.api;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import me.osm.gazetteer.web.api.API.GazetteerAPIException;

import org.json.JSONObject;

public class InverseGeocodeAPI implements API {

	@Override
	public JSONObject request(HttpServletRequest request) 
			throws GazetteerAPIException, IOException {
		return null;
	}

}
