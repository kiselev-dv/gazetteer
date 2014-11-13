package me.osm.gazetteer.web;

import java.io.FileNotFoundException;
import java.io.IOException;

import me.osm.gazetteer.web.api.FeatureAPI;
import me.osm.gazetteer.web.api.ImportAPI;
import me.osm.gazetteer.web.api.InverseGeocodeAPI;
import me.osm.gazetteer.web.api.OSMDocAPI;
import me.osm.gazetteer.web.api.SearchAPI;
import me.osm.gazetteer.web.api.Sitemap;
import me.osm.gazetteer.web.api.SnapshotsAPI;
import me.osm.gazetteer.web.api.Static;
import me.osm.gazetteer.web.api.SuggestAPI;
import me.osm.gazetteer.web.postprocessor.AllowOriginPP;
import me.osm.gazetteer.web.postprocessor.LastModifiedHeaderPostprocessor;
import me.osm.gazetteer.web.serialization.SerializationProvider;
import me.osm.gazetteer.web.utils.OSMDocSinglton;

import org.apache.commons.codec.binary.Hex;
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
	private static Configuration config;
	
	public static void main(String[] args) throws Exception {
		
		config = loadEnvironment(args);
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
						return Hex.encodeHexString(DigestUtils.sha(header))
								.equals(config.getAdminPasswordHash());
					}
					
				})
				.addMessageObserver(new SimpleConsoleLogMessageObserver());

		defineRoutes(config, server);
		
		SearchAPI.updateQueryNorm();
		
		RoutesMetadataPlugin routesMetadataPlugin = new RoutesMetadataPlugin();
		routesMetadataPlugin.register(server);

		server.bind(config.getPort());
		Runtime runtime = Runtime.getRuntime();
		Thread thread = new Thread(new ShutDownListener());
        runtime.addShutdownHook(thread);
	}

	private static void defineRoutes(Configuration config, RestExpress server) {
		
		String root = config.getWebRoot();

		System.out.println("Define routes with web root: " + root);
		
		OSMDocSinglton.initialize(config.getPoiCatalogPath());
		
		server.uri(root + "/feature/_search",
				new SearchAPI())
				.method(HttpMethod.GET)
				.name(Constants.FEATURE_URI)
				.flag(Flags.Auth.PUBLIC_ROUTE)
				.parameter(Parameters.Cache.MAX_AGE, 3600).asMetadata().toString();

		server.uri(root + "/feature/_suggest",
				new SuggestAPI())
				.method(HttpMethod.GET)
				.name(Constants.FEATURE_URI)
				.flag(Flags.Auth.PUBLIC_ROUTE)
				.parameter(Parameters.Cache.MAX_AGE, 3600).asMetadata().toString();
		
		server.uri(root + "/_inverse",
				new InverseGeocodeAPI())
				.method(HttpMethod.GET)
				.name(Constants.FEATURE_URI)
				.flag(Flags.Auth.PUBLIC_ROUTE)
				.parameter(Parameters.Cache.MAX_AGE, 3600);

		server.uri(root + "/osmdoc/hierarchy/{lang}/{id}",
				new OSMDocAPI())
				.method(HttpMethod.GET)
				.flag(Flags.Auth.PUBLIC_ROUTE)
				.parameter("handler", "hierarchy")
				.parameter(Parameters.Cache.MAX_AGE, 3600);

		server.uri(root + "/osmdoc/poi-class/{lang}/{id}",
				new OSMDocAPI())
				.method(HttpMethod.GET)
				.flag(Flags.Auth.PUBLIC_ROUTE)
				.parameter("handler", "poi-class")
				.parameter(Parameters.Cache.MAX_AGE, 3600);

		server.uri(root + "/_import",
				new ImportAPI())
				.method(HttpMethod.GET)
				.flag(Flags.Cache.DONT_CACHE);

		server.uri(root + "/feature",
				new FeatureAPI())
				.alias(root + "/feature/{id}.{format}")
				.method(HttpMethod.GET)
				.flag(Flags.Auth.PUBLIC_ROUTE)
				.flag(Flags.Cache.DONT_CACHE);

		server.uri(root + "/snapshot/.*",
				new SnapshotsAPI(config))
				.method(HttpMethod.GET)
				.flag(Flags.Auth.PUBLIC_ROUTE)
				.noSerialization();
		
		if(config.isServeStatic()) {
			server.uri(root + "/static/.*", new Static())
				.method(HttpMethod.GET)
				.flag(Flags.Auth.PUBLIC_ROUTE)
				.noSerialization();
		}
		
		server.uri(root + "/sitemap.*", new Sitemap(config))
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
