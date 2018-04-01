package me.osm.gazetteer.psqlsearch.api;

import org.restexpress.Request;
import org.restexpress.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

import me.osm.gazetteer.psqlsearch.api.search.Search;

public class SearchAPIAdapter implements SearchAPI {
	
	private static final Logger log = LoggerFactory.getLogger(SearchAPIAdapter.class);
	
	private static final int DEFAULT_PAGE_SIZE = 20;
	
	private static final String Q_PARAM = "q";
	private static final String PREFIX_PARAM = "prefix";
	
	private static final String PAGE_PARAM = "page";
	private static final String PAGE_SIZE = "size";

	private static final String LAT_PARAM = "center_lat";

	private static final String LON_PARAM = "center_lon";
	
	@Inject
	private Search search;

	@Override
	public ResultsWrapper read(Request request, Response response) {
		
		String query = request.getHeader(Q_PARAM);
		boolean prefix = getBoolean(request, PREFIX_PARAM, false);
		
		int pageSize = getPageSize(request);
		int page = getPage(request);
		
		Double lon = getLon(request);
		Double lat = getLat(request);
		
		log.info("search {}", query);
		
		return search.search(query, prefix, lon, lat, false, page, pageSize);
	}

	private boolean getBoolean(Request request, String header, boolean defValue) {
		String val = request.getHeader(header);
		if (val != null && "true".equals(val.toLowerCase())) {
			return true;
		}
		
		return false;
	}

	private Double getLat(Request request) {
		if(request.getHeader(LAT_PARAM) != null) {
			return getDoubleOrNull(request.getHeader(LAT_PARAM));
		}
		return null;
	}
	
	private Double getLon(Request request) {
		if(request.getHeader(LON_PARAM) != null) {
			return getDoubleOrNull(request.getHeader(LON_PARAM));
		}
		return null;
	}

	private Double getDoubleOrNull(String s) {
		try {
			return Double.valueOf(s);
		}
		catch (Exception e) {
			return null;
		}
	}

	private int getPage(Request request) {
		int page = 1;
		if(request.getHeader(PAGE_PARAM) != null) {
			page = Integer.parseInt(request.getHeader(PAGE_PARAM));
			if(page < 1) {
				page = 1;
			}
		}
		return page;
	}

	private int getPageSize(Request request) {
		int pageSize = DEFAULT_PAGE_SIZE;
		if(request.getHeader(PAGE_SIZE) != null) {
			pageSize = Integer.parseInt(request.getHeader(PAGE_SIZE));
		}
		return pageSize;
	}

}
