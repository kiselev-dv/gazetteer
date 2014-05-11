package me.osm.gazetteer.web.api;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

public class ServletUtils {

	public static void writeJson(String result, HttpServletResponse response) throws IOException {
		
		response.setCharacterEncoding("UTF-8");
		response.setContentType("application/json");
		
		response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate"); // HTTP 1.1.
		response.setHeader("Pragma", "no-cache"); // HTTP 1.0.
		response.setDateHeader("Expires", 0); // Proxies.
		
		response.getWriter().print(result);
		response.getWriter().flush();
	}
	
}
