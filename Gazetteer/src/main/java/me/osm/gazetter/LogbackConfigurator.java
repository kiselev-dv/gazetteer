package me.osm.gazetter;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import ch.qos.logback.core.spi.ContextAwareBase;

public class LogbackConfigurator extends ContextAwareBase implements Configurator {
	
	static String outFile;
	static String logPrefix = "";
	static String level = "INFO";
	static boolean muteConsole = false;

	@Override
	public void configure(LoggerContext lc) {
		
		String pattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} %marker %-5level %logger{36} - %msg%n";
		if (StringUtils.stripToNull(logPrefix) != null) {
			pattern = logPrefix + " " + pattern;
		}

		Logger rootLogger = lc.getLogger(Logger.ROOT_LOGGER_NAME);
		rootLogger.detachAndStopAllAppenders();
		
		if (!muteConsole) {
			
			ConsoleAppender<ILoggingEvent> ca = new ConsoleAppender<ILoggingEvent>();
			ca.setContext(lc);
			ca.setName("console");
			LayoutWrappingEncoder<ILoggingEvent> encoder = new LayoutWrappingEncoder<ILoggingEvent>();
			encoder.setContext(lc);
			
			PatternLayout layout = new PatternLayout();
			layout.setPattern(pattern);
			layout.setContext(lc);
			layout.start();
			
			encoder.setLayout(layout);
			
			ca.setEncoder(encoder);
			ca.start();
			
			rootLogger.addAppender(ca);
		}

		if (outFile != null) {
			FileAppender<ILoggingEvent> fa = new FileAppender<>();
			fa.setContext(lc);
			fa.setName("file");
			
			LayoutWrappingEncoder<ILoggingEvent> encoder = new LayoutWrappingEncoder<ILoggingEvent>();
			encoder.setContext(lc);
			
			PatternLayout layout = new PatternLayout();
			layout.setPattern(pattern);
			layout.setContext(lc);
			layout.start();
			
			encoder.setLayout(layout);
			
			fa.setEncoder(encoder);
			fa.setFile(outFile);
			fa.start();
			
			rootLogger.addAppender(fa);
		}

		rootLogger.setLevel(Level.valueOf(level));
		
	}

	public static void configureStatic() {
		LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
		new LogbackConfigurator().configure(lc);
	}

}
