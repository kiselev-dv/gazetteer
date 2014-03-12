package me.osm.gazetter.pointlocation;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import me.osm.gazetter.striper.GeoJsonWriter;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.DateUtils;

public class SortAndUpdateTask implements Runnable {

	private File stripeF;
	
	private static final AtomicInteger counter = new AtomicInteger();

	public SortAndUpdateTask(File stripeF) {
		this.stripeF = stripeF;
	}

	@Override
	public void run() {
		try {
			
			@SuppressWarnings("unchecked")
			List<String> lines = FileUtils.readLines(stripeF);
			
			Collections.sort(lines, new Comparator<String>() {

				@Override
				public int compare(String paramT1, String paramT2) {
					String id1 = GeoJsonWriter.getId(paramT1);
					String id2 = GeoJsonWriter.getId(paramT2);

					if (id1.equals(id2)) {
						Date d1 = GeoJsonWriter.getTimestamp(paramT1);
						Date d2 = GeoJsonWriter.getTimestamp(paramT2);
						
						return d2.compareTo(d1);
					}

					return id1.compareTo(id2);
				}

			});

			Iterator<String> iterator = lines.iterator();

			String prevId = null;
			Date prevTimestamp = null;

			while (iterator.hasNext()) {
				String line = iterator.next();

				String id = GeoJsonWriter.getId(line);
				Date timestamp = GeoJsonWriter.getTimestamp(line);

				if (prevId != null && prevTimestamp != null
						&& id.equals(prevId) && timestamp.before(prevTimestamp)) {
					iterator.remove();
					counter.getAndIncrement();
				}

				prevId = id;
				prevTimestamp = timestamp;
			}

			FileUtils.writeLines(stripeF, lines);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}

	}
	
	public static int countUpdatedLines() {
		return counter.get();
	}

}
