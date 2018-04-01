package me.osm.gazetteer.psqlsearch.server;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.restexpress.RestExpress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Guice;
import com.google.inject.Injector;

import me.osm.gazetteer.psqlsearch.server.postprocessor.AllowOriginPP;
import me.osm.gazetteer.psqlsearch.server.postprocessor.LastModifiedHeaderPostprocessor;
import me.osm.gazetteer.psqlsearch.server.postprocessor.MarkHeaderPostprocessor;

public class REServer {
	
	private static final Logger log = LoggerFactory.getLogger(REServer.class);
	private static final REServer INSTANCE = new REServer();

	public static final REServer getInstance() {
		return INSTANCE;
	}

	private final RestExpress server;
	
	private REServer () {
		Injector injector = Guice.createInjector(new REServerModule());
		
		server = new RestExpress()
				.setUseSystemOut(false)
				.setPort(getPort())
				.setName(getServerName())
				.addPostprocessor(new LastModifiedHeaderPostprocessor())
				.addPostprocessor(new AllowOriginPP())
				.addPostprocessor(new MarkHeaderPostprocessor())
				.addPreprocessor(new BasikAuthPreprocessor(getRealmName(), getAdminPasswordHash()));
		
		REServerRoutes routes = injector.getInstance(REServerRoutes.class);
		routes.defineRoutes(server, getWebRoot());
		
		server.addMessageObserver(new HttpLogger());
		server.bind(getPort());
		
		log.info("Listen on port: {}", getPort());

		server.awaitShutdown();
	}

	private String getWebRoot() {
		return "";
	}

	private String getRealmName() {
		return "gazetteer-search";
	}


	private String getServerName() {
		return "gazetteer";
	}
	
	private int getPort() {
		return 8080;
	}

	private String getAdminPasswordHash() {
		return Hex.encodeHexString(DigestUtils.sha("test"));
	}
	
	public static void main(String[] args) {
		REServer.getInstance();
	}

}
