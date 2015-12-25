package me.osm.gazetteer.web;

import me.osm.gazetteer.web.api.FeatureAPI;
import me.osm.gazetteer.web.api.GeocodeCSVAPI;
import me.osm.gazetteer.web.api.HealthAPI;
import me.osm.gazetteer.web.api.ImportLocations;
import me.osm.gazetteer.web.api.ImportOSMDoc;
import me.osm.gazetteer.web.api.IndexAPI;
import me.osm.gazetteer.web.api.InverseGeocodeAPI;
import me.osm.gazetteer.web.api.MetaInfoAPI;
import me.osm.gazetteer.web.api.OSMDocAPI;
import me.osm.gazetteer.web.api.SearchAPI;
import me.osm.gazetteer.web.api.Sitemap;
import me.osm.gazetteer.web.api.SnapshotsAPI;
import me.osm.gazetteer.web.api.Static;
import me.osm.gazetteer.web.api.StatisticAPI;
import me.osm.gazetteer.web.api.SuggestAPI;

import org.jboss.netty.handler.codec.http.HttpMethod;
import org.restexpress.Flags;
import org.restexpress.Parameters;
import org.restexpress.RestExpress;
import org.slf4j.LoggerFactory;

public class Routes {
	
	public static void defineRoutes(RestExpress server) {
		
		Configuration config = GazetteerWeb.config();
		String root = config.getWebRoot();

		LoggerFactory.getLogger(Routes.class).info("Define routes with web root: {}", root);
		
		server.uri(root + "/info.{format}",
				new MetaInfoAPI(server))
				.flag(Flags.Auth.PUBLIC_ROUTE)
				.method(HttpMethod.GET);
		
		server.uri(root + "/location/_import",
				new ImportLocations())
				.method(HttpMethod.GET)
				.flag(Flags.Cache.DONT_CACHE);

		SearchAPI searchAPIInstance = new SearchAPI();
		
		server.uri(root + "/location/_search",
				searchAPIInstance)
				.method(HttpMethod.GET)
				.name("feature")
				.flag(Flags.Auth.PUBLIC_ROUTE)
				.parameter(Parameters.Cache.MAX_AGE, 3600);

		server.uri(root + "/location/_geocode_csv",
				new GeocodeCSVAPI(searchAPIInstance))
				.method(HttpMethod.GET)
				.flag(Flags.Auth.PUBLIC_ROUTE);

		server.uri(root + "/location/_suggest",
				new SuggestAPI())
				.method(HttpMethod.GET)
				.name("feature")
				.flag(Flags.Auth.PUBLIC_ROUTE)
				.parameter(Parameters.Cache.MAX_AGE, 3600);

		server.uri(root + "/location/{id}/{_related}",
				new FeatureAPI())
					.alias(root + "/location/{id}")
					.method(HttpMethod.GET)
					.name("feature")
					.flag(Flags.Auth.PUBLIC_ROUTE)
					.parameter(Parameters.Cache.MAX_AGE, 3600);
		
		server.uri(root + "/location/latlon/{lat}/{lon}/{_related}",
				new InverseGeocodeAPI())
				.alias(root + "/location/latlon/{lat}/{lon}")
				.alias(root + "/_inverse")
				.method(HttpMethod.GET)
				.name("feature")
				.flag(Flags.Auth.PUBLIC_ROUTE)
				.parameter(Parameters.Cache.MAX_AGE, 3600);

		server.uri(root + "/osmdoc/hierarchy/{lang}/{id}",
				new OSMDocAPI())
				.method(HttpMethod.GET)
				.flag(Flags.Auth.PUBLIC_ROUTE)
				.parameter("handler", "hierarchy")
				.parameter(Parameters.Cache.MAX_AGE, 3600);

		server.uri(root + "/osmdoc/_import",
				new ImportOSMDoc())
				.method(HttpMethod.GET)
				.parameter(Parameters.Cache.MAX_AGE, 3600);

		server.uri(root + "/osmdoc/statistic/tagvalues/{poi_class}",
				new StatisticAPI())
				.method(HttpMethod.GET)
				.flag(Flags.Auth.PUBLIC_ROUTE)
				.defaultFormat("json")
				.parameter(Parameters.Cache.MAX_AGE, 3600);

		server.uri(root + "/osmdoc/poi-class/{lang}/{id}",
				new OSMDocAPI())
				.method(HttpMethod.GET)
				.flag(Flags.Auth.PUBLIC_ROUTE)
				.parameter("handler", "poi-class")
				.parameter(Parameters.Cache.MAX_AGE, 3600);

		server.uri(root + "/health.{format}",
				new HealthAPI())
				.method(HttpMethod.GET)
				.flag(Flags.Auth.PUBLIC_ROUTE);

		server.uri(root + "/index",
				new IndexAPI())
				.method(HttpMethod.GET);

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
		
		server.uri(root + "/sitemap.*", new Sitemap())
			.method(HttpMethod.GET)
			.flag(Flags.Auth.PUBLIC_ROUTE)
			.noSerialization();
		
	}
}
