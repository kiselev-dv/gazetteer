package me.osm.gazetteer.web.api;

import java.lang.reflect.Field;

import me.osm.gazetteer.web.Main;
import me.osm.gazetteer.web.api.meta.Endpoint;
import me.osm.gazetteer.web.api.meta.Metadata;
import me.osm.gazetteer.web.api.meta.Parameter;

import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.RestExpress;
import org.restexpress.domain.metadata.RouteMetadata;
import org.restexpress.domain.metadata.UriMetadata;
import org.restexpress.route.RouteBuilder;
import org.restexpress.util.Callback;

public class MetaInfoAPI implements DocumentedApi {

	private RestExpress server;
	
	public MetaInfoAPI(RestExpress server) {
		this.server = server;
	}
	
	public Metadata read(Request req, Response res)	{

		final Metadata meta = new Metadata();
		
		server.iterateRouteBuilders(new Callback<RouteBuilder>() {
			
			@Override
			public void process(RouteBuilder rb) {
				try {
					Field f = RouteBuilder.class.getDeclaredField("controller");
					f.setAccessible(true);
					Object controller = f.get(rb);
					if(controller instanceof DocumentedApi) {
						RouteMetadata rbMeta = rb.asMetadata();
						
						Endpoint ep = ((DocumentedApi)controller).getMeta(rbMeta.getUri());
						ep.setHttpMethods(rbMeta.getMethods());
						
						meta.getEndpoints().add(ep);
					}
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
			
		});
		
		return meta;
		
	}

	@Override
	public Endpoint getMeta(UriMetadata uriMetadata) {
		Endpoint meta = new Endpoint(uriMetadata.getPattern(), "info", 
				"Returns information about application data endpoints, their usage and parameters.");
		
		meta.getPathParameters().add(new Parameter("format", "Answer format. json or xml."));
		
		return meta;
	}
	
}
