package me.osm.gazetteer.sortupdate;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import me.osm.gazetteer.striper.GeoJsonWriter;

import org.joda.time.DateTime;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SortAndUpdateTask implements Runnable {

	private static final Logger log = LoggerFactory.getLogger(SortAndUpdateTask.class);

	private File stripeF;

	private static final AtomicInteger counter = new AtomicInteger();

	public SortAndUpdateTask(File stripeF) {
		this.stripeF = stripeF;
	}

	@Override
	public void run() {
		try {

			List<String> lines = me.osm.gazetteer.utils.FileUtils.readLines(stripeF);

			Collections.sort(lines, new Comparator<String>() {

				@Override
				public int compare(String paramT1, String paramT2) {
					String id1 = GeoJsonWriter.getId(paramT1);
					String id2 = GeoJsonWriter.getId(paramT2);

					if (id1.equals(id2)) {
						DateTime d1 = GeoJsonWriter.getTimestamp(paramT1);
						DateTime d2 = GeoJsonWriter.getTimestamp(paramT2);

						return d2.compareTo(d1);
					}

					return id1.compareTo(id2);
				}

			});

			Iterator<String> iterator = lines.iterator();

			String prevId = null;
			DateTime prevTimestamp = null;

			while (iterator.hasNext()) {
				String line = iterator.next();

				String id = GeoJsonWriter.getId(line);
				DateTime timestamp = GeoJsonWriter.getTimestamp(line);

				if("remove".equals(GeoJsonWriter.getAction(line))) {
					iterator.remove();
					counter.getAndIncrement();

					log.info("Remove feature. Reason: {}", new JSONObject(line).optString("actionDetailed"));

					//do not save removed id and timestamp
					//into prevId and prevTimestamp
					continue;
				}

				if (prevId != null && prevTimestamp != null
						&& id.equals(prevId) && timestamp.isBefore(prevTimestamp)) {
					iterator.remove();
					counter.getAndIncrement();
				}

				prevId = id;
				prevTimestamp = timestamp;
			}

			me.osm.gazetteer.utils.FileUtils.writeLines(stripeF, lines);
		} catch (IOException e) {
			throw new RuntimeException("Failed to update " + this.stripeF, e);
		}

	}

	public static int countUpdatedLines() {
		return counter.get();
	}

}
