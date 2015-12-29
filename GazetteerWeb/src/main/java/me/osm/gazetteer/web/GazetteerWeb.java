package me.osm.gazetteer.web;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;

import me.osm.gazetteer.web.postprocessor.AllowOriginPP;
import me.osm.gazetteer.web.postprocessor.LastModifiedHeaderPostprocessor;
import me.osm.gazetteer.web.postprocessor.MarkHeaderPostprocessor;
import me.osm.gazetteer.web.serialization.SerializationProvider;
import me.osm.gazetteer.web.utils.OSMDocProperties;
import me.osm.gazetteer.web.utils.OSMDocSinglton;
import me.osm.osmdoc.localization.L10n;

import org.restexpress.RestExpress;
import org.restexpress.util.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class GazetteerWeb {
	
	private static final Logger LOG = LoggerFactory.getLogger(GazetteerWeb.class);

	private static class ShutDownListener implements Runnable
	{
	    @Override
	    public void run()
	    {
	    	ESNodeHolder.stopNode();
	    	server.shutdown();
	    }
	}
	
	private static RestExpress server;
	private volatile static Configuration config = new Configuration();
	private final static OSMDocProperties osmdocProperties = new OSMDocProperties();
	private static final Injector injector = Guice.createInjector(new AppInjector());
	
	public static void main(String[] args) throws Exception {
		
		initLog();
		LOG.info("Start GazetterWeb server");
		
		try {
			config = loadEnvironment(args);
			ESNodeHolder.getClient();

			initOSMDoc();
			
			RestExpress.setSerializationProvider(new SerializationProvider());
			
			server = new RestExpress()
				.setUseSystemOut(false)
				.setName(config.getName())
				.addPostprocessor(new LastModifiedHeaderPostprocessor())
				.addPostprocessor(new AllowOriginPP())
				.addPostprocessor(new MarkHeaderPostprocessor())
				.addPreprocessor(new BasikAuthPreprocessor(null));
			
			Routes.defineRoutes(server);
			
			server.addMessageObserver(new HttpLogger());
			
			LOG.trace("Bind to port {}", config.getPort());
			server.bind(config.getPort());
			
			LOG.trace("Create shutdown hook");
			Runtime runtime = Runtime.getRuntime();
			Thread thread = new Thread(new ShutDownListener());
			runtime.addShutdownHook(thread);
			
			LOG.info("{} server listening on port {}", config.getName(), config.getPort());
		}
		catch (Exception e) {
			LOG.error("Initialization error.", e);
		}
	}

	private static void initOSMDoc() {
		try {
			if(!"jar".equals(config.getPoiCatalogPath())) {
				L10n.setCatalogPath(config.getPoiCatalogPath());
			}
			
			Properties props = new Properties();
			InputStreamReader reader = new InputStreamReader(new FileInputStream(new File("config/osmdoc.properties")));
			props.load(reader);
			reader.close();
			
			osmdocProperties.load(props);
			
			OSMDocSinglton.initialize(config.getPoiCatalogPath());
			
		}
		catch (Throwable t) {
			LOG.error("Cant initialize OSMDoc", t);
		}
	}

	private static void initLog() {
		// assume SLF4J is bound to logback in the current environment
	    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
	    
		try {
	      JoranConfigurator configurator = new JoranConfigurator();
	      configurator.setContext(context);
	      // Call context.reset() to clear any previous configuration, e.g. default 
	      // configuration. For multi-step configuration, omit calling context.reset().
	      context.reset(); 
	      configurator.doConfigure("config/logback.xml");
	    } catch (JoranException je) {
	      // StatusPrinter will handle this
	    }
		
		StatusPrinter.printInCaseOfErrorsOrWarnings(context);

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
	
	public static Configuration config() {
		return config;
	}

	public static Injector injector() {
		return injector;
	}
	
	public static OSMDocProperties osmdocProperties() {
		return osmdocProperties;
	}
	
}
