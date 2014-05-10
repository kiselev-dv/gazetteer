package me.osm.gazetteer.web;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public interface API {

	void request(HttpServletRequest request, HttpServletResponse response);

}
