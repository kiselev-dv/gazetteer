package me.osm.gazetteer.web.api.utils;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.json.JSONObject;
import org.restexpress.Request;

import com.google.inject.Singleton;

@Singleton
public class PaginatorImpl implements Paginator {
	
	private static final int DEFAULT_PAGE_SIZE = 20;
	
	private static final String PAGE_PARAM = "page";
	private static final String PAGE_SIZE = "size";
	
	/* (non-Javadoc)
	 * @see me.osm.gazetteer.web.api.utils.Paginator#applyPaging(org.restexpress.Request, org.elasticsearch.action.search.SearchRequestBuilder)
	 */
	@Override
	public void patchSearchQ(Request request,
			SearchRequestBuilder searchQ) {
		
		int pageSize = DEFAULT_PAGE_SIZE;
		if(request.getHeader(PAGE_SIZE) != null) {
			pageSize = Integer.parseInt(request.getHeader(PAGE_SIZE));
		}
		
		int page = 1;
		if(request.getHeader(PAGE_PARAM) != null) {
			page = Integer.parseInt(request.getHeader(PAGE_PARAM));
			if(page < 1) {
				page = 1;
			}
		}
		searchQ.setSize(pageSize);
		searchQ.setFrom((page - 1) * pageSize);
	}

	/* (non-Javadoc)
	 * @see me.osm.gazetteer.web.api.utils.Paginator#resultPaging(org.restexpress.Request, org.json.JSONObject)
	 */
	@Override
	public void patchAnswer(Request request, JSONObject answer) {
		int pageSize = DEFAULT_PAGE_SIZE;
		if(request.getHeader(PAGE_SIZE) != null) {
			pageSize = Integer.parseInt(request.getHeader(PAGE_SIZE));
		}
		
		int page = 1;
		if(request.getHeader(PAGE_PARAM) != null) {
			page = Integer.parseInt(request.getHeader(PAGE_PARAM));
			if(page < 1) {
				page = 1;
			}
		}
		answer.put(PAGE_SIZE, pageSize);
		answer.put("from", (page - 1) * pageSize);
		answer.put(PAGE_PARAM, page);
		
	}
}
