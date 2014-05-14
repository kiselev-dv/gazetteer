package me.osm.gazetteer.web;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import me.osm.gazetteer.web.api.API;
import me.osm.gazetteer.web.api.API.GazetteerAPIException;
import me.osm.gazetteer.web.api.FeatureAPI;
import me.osm.gazetteer.web.api.InverseGeocodeAPI;
import me.osm.gazetteer.web.api.ListFeaturesAPI;
import me.osm.gazetteer.web.api.MassiveAPI;
import me.osm.gazetteer.web.api.SearchAPI;
import me.osm.gazetteer.web.api.ServletUtils;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

/**
 * Servlet implementation class SearchFacade
 */
@WebServlet("/api/*")
public class ApiDispatchServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
	public static final Map<String, API> apis = new HashMap<String, API>();
	
	public static final class ApiDispatchException extends Exception {

		private static final long serialVersionUID = -1893317883061533965L;

		public ApiDispatchException(String string) {
			super(string);
		}

		public ApiDispatchException() {
			super();
		}
		
	}
	
	static {
		apis.put("search", new SearchAPI());
		apis.put("feature", new FeatureAPI());
		apis.put("list", new ListFeaturesAPI());
		apis.put("igeocode", new InverseGeocodeAPI());
		apis.put("massive", new MassiveAPI());
	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		try {
			API api = dispatch(request);
			if(api != null) {
				String pathInfo = request.getPathInfo();
				String[] pathParts = StringUtils.split(pathInfo, '/');
				if(pathParts.length > 1) {
					String format = pathParts[1];
				}
			}
		
			JSONObject result = api.request(request);
			
			ServletUtils.writeJson(result.toString(), response);
		}
		catch (ApiDispatchException e) {
			throw new ServletException("Can't dispatch api request", e);
		}
		catch (GazetteerAPIException e) {
			throw new ServletException("Can't perform api request", e);
		}
		
	}

	private API dispatch(HttpServletRequest request) throws ApiDispatchException {
		
		String pathInfo = request.getPathInfo();
		if(pathInfo != null) {
			String[] pathParts = StringUtils.split(pathInfo, '/');
			
			if(pathParts.length > 0) {
				
				String apiName = pathParts[0];
				API api = apis.get(apiName.toLowerCase());
				
				if(api != null) {
					return api;
				}
				else {
					throw new ApiDispatchException("Can't find api with name " + apiName);
				}
			}
			
		}
		
		throw new ApiDispatchException();
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}

}
