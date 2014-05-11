package me.osm.gazetteer.web.api;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public interface API {
	
	public static final class GazetteerAPIException extends Exception {

		private static final long serialVersionUID = 5965216362210644199L;

		public GazetteerAPIException() {
			super();
		}

		public GazetteerAPIException(String msg) {
			super(msg);
		}

		public GazetteerAPIException(String msg, Throwable cause) {
			super(msg, cause);
		}
		
	}

	void request(HttpServletRequest request, HttpServletResponse response) throws GazetteerAPIException, IOException;
	void setFormat(String format);
	void setDefaultFormat();

}
