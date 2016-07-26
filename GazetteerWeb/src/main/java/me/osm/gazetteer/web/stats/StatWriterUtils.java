package me.osm.gazetteer.web.stats;

import java.util.Set;

import me.osm.gazetteer.web.stats.APIRequest.APIRequestBuilder;

import org.apache.commons.lang3.StringUtils;
import org.jboss.netty.handler.codec.http.Cookie;
import org.jboss.netty.handler.codec.http.CookieDecoder;
import org.restexpress.Request;

public final class StatWriterUtils {
	
	private static final CookieDecoder cookieDecoder = new CookieDecoder();;

	public static final APIRequestBuilder fillFromRequest(Request req) {
		APIRequestBuilder builder = APIRequestBuilder.builder();
		fillFromRequest(builder, req);
		return builder;
	}

	public static void fillFromRequest(APIRequestBuilder b, Request req) {
		b.userIp(req.getRemoteAddress().getAddress().toString());
		
		String langHeader = req.getHeader("HTTP_ACCEPT_LANGUAGE");
		if (langHeader != null) {
			String[] split = StringUtils.split(langHeader, ",;");
			if (split.length > 0) {
				b.lang(split[0]);
			}
		}
		
		String session = req.getHeader("site_session");
		if (session != null) {
			b.sessionId(session);
		}
		
		Cookie userCookie = getUserCookie(req);
		if (userCookie != null) {
			b.userId(userCookie.getValue()); 
		}
	}

	private static Cookie getUserCookie(Request req) {
		String cookieString = req.getHeader("Cookie");
	    if (cookieString != null) {
			Set<Cookie> cookies = cookieDecoder.decode(cookieString);
			for (Cookie c : cookies) {
				if("user".equals(c.getName())) {
					return c;
				}
			}
	    }
	    
	    return null;
	}
	
}
