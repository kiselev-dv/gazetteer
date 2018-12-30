package me.osm.gazetteer.utils;

import java.lang.reflect.Method;
import java.nio.MappedByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DirectByteBufferCleaner {

	private static final Logger log = LoggerFactory.getLogger(DirectByteBufferCleaner.class);

	private static volatile Class<?> cleanerClass = null;
	private static volatile Method clean = null;

	static {
		try {
			cleanerClass = Class.forName("sun.misc.Cleaner");
		}
		catch (ClassNotFoundException cnfe2) {
			log.error("Failed to find java.lang.ref.Cleaner or sun.misc.Cleaner");
		}

		if (cleanerClass != null) {
			try {
				clean = cleanerClass.getMethod("clean");
				clean.setAccessible(true);
			}
			catch (Exception e) {
				log.error("Failed to set clean accessible");
			}
		}
	}

	public static final void close(MappedByteBuffer buffer) {
		if (cleanerClass != null) {
			try {
				Method cleaner = buffer.getClass().getMethod("cleaner");
				cleaner.setAccessible(true);
				clean.invoke(cleaner.invoke(buffer));
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

}
