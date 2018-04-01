package me.osm.gazetteer.psqlsearch.server.postprocessor;

import java.util.Date;

import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.pipeline.Postprocessor;

import com.strategicgains.util.date.DateAdapter;
import com.strategicgains.util.date.HttpHeaderTimestampAdapter;

/**
 * Assigns the Last-Modified HTTP header on the response for GET responses, if
 * applicable.
 * 
 * @author toddf
 * @since May 15, 2012
 */
public class LastModifiedHeaderPostprocessor implements Postprocessor {

	public static final String LAST_MODIFIED = "Last-Modified";
	private static final DateAdapter fmt = new HttpHeaderTimestampAdapter();

	/**
	 * Encode date and add header to response
	 * */
	public static void addHeader(Response response, Date date) {
		
		response.addHeader(LAST_MODIFIED,
				fmt.format(date));
		
	}
	
	@Override
	public void process(Request request, Response response) {
		
		if (!request.isMethodGet()) {
			return;
		}

		if (!response.hasBody()) {
			return;
		}

		Object body = response.getBody();

		if (!response.hasHeader(LAST_MODIFIED)
				&& body.getClass().isAssignableFrom(Timestamped.class)) {
		
			addHeader(response, ((Timestamped) body).getUpdatedAt());
		}
	}
}
