package me.osm.gazetteer.web;

import java.io.FileNotFoundException;
import java.io.IOException;

import me.osm.gazetteer.web.api.FeatureAPI;
import me.osm.gazetteer.web.api.ImportAPI;
import me.osm.gazetteer.web.api.SearchAPI;
import me.osm.gazetteer.web.api.Sitemap;
import me.osm.gazetteer.web.api.Static;
import me.osm.gazetteer.web.postprocessor.AllowOriginPP;
import me.osm.gazetteer.web.postprocessor.LastModifiedHeaderPostprocessor;
import me.osm.gazetteer.web.serialization.SerializationProvider;

import org.apache.commons.codec.digest.DigestUtils;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.restexpress.Flags;
import org.restexpress.Parameters;
import org.restexpress.Request;
import org.restexpress.RestExpress;
import org.restexpress.exception.UnauthorizedException;
import org.restexpress.pipeline.SimpleConsoleLogMessageObserver;
import org.restexpress.preprocessor.HttpBasicAuthenticationPreprocessor;
import org.restexpress.route.Route;
import org.restexpress.util.Environment;

import com.strategicgains.restexpress.plugin.route.RoutesMetadataPlugin;

public class Main {
	
	private static class ShutDownListener implements Runnable
	{
	    @Override
	    public void run()
	    {
	    	ESNodeHodel.stopNode();
	    	server.shutdown();
	    }
	}
	
	private static volatile RestExpress server;
	
	public static void main(String[] args) throws Exception {
		
		Configuration config = loadEnvironment(args);
		ESNodeHodel.getClient();
		
		RestExpress.setSerializationProvider(new SerializationProvider());
		
		server = new RestExpress()
				.setName(config.getName())
				.addPostprocessor(new LastModifiedHeaderPostprocessor())
				.addPostprocessor(new AllowOriginPP())
				.addPreprocessor(new HttpBasicAuthenticationPreprocessor(null){
					@Override
					public void process(Request request) {

						Route route = request.getResolvedRoute();

						if (route != null && (route.isFlagged(Flags.Auth.PUBLIC_ROUTE)
							|| route.isFlagged(Flags.Auth.NO_AUTHENTICATION)))
						{
							return;
						}
						
						super.process(request);
						
						if(!"admin".equals(request.getHeader(X_AUTHENTICATED_USER)) ||
								!checkPass(request.getHeader(X_AUTHENTICATED_PASSWORD))) {
							throw new UnauthorizedException();
						}
					}

					private boolean checkPass(String header) {
						return DigestUtils.md5Hex(header).equals("21232f297a57a5a743894a0e4a801fc3");
					}
					
				})
				.addMessageObserver(new SimpleConsoleLogMessageObserver());

		defineRoutes(config, server);
		
		new RoutesMetadataPlugin().register(server);

		server.bind(config.getPort());
		Runtime runtime = Runtime.getRuntime();
		Thread thread = new Thread(new ShutDownListener());
        runtime.addShutdownHook(thread);
	}

	private static void defineRoutes(Configuration config, RestExpress server) {
		
		server.uri("/_search",
				new SearchAPI())
				.method(HttpMethod.GET)
				.name(Constants.FEATURE_URI)
				.flag(Flags.Auth.PUBLIC_ROUTE)
				.parameter(Parameters.Cache.MAX_AGE, 3600);

		server.uri("/_import",
				new ImportAPI())
				.method(HttpMethod.GET)
				.flag(Flags.Cache.DONT_CACHE);

		server.uri("/feature",
				new FeatureAPI())
				.alias("/feature/{id}.xml")
				.method(HttpMethod.GET)
				.flag(Flags.Auth.PUBLIC_ROUTE)
				.flag(Flags.Cache.DONT_CACHE);
		
		server.uri("/static/.*", new Static())
			.method(HttpMethod.GET)
			.flag(Flags.Auth.PUBLIC_ROUTE)
			.noSerialization();
		
		server.uri("/sitemap.*", new Sitemap(config))
			.method(HttpMethod.GET)
			.flag(Flags.Auth.PUBLIC_ROUTE)
			.noSerialization();
		
	}

	private static Configuration loadEnvironment(String[] args)
			throws FileNotFoundException, IOException {
		if (args.length > 0) {
			return Environment.from(args[0], Configuration.class);
		}

		try {
			return Environment.fromDefault(Configuration.class);
		}
		catch (Exception e) {
			return new Configuration();
		}
	}
}
