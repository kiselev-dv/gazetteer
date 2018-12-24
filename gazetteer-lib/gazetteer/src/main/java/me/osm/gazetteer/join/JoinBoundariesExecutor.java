package me.osm.gazetteer.join;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import me.osm.gazetteer.join.out_handlers.JoinOutHandler;
import me.osm.gazetteer.join.util.BoundaryCortage;
import me.osm.gazetteer.utils.FileUtils;
import me.osm.gazetteer.Options;
import me.osm.gazetteer.addresses.AddressesParser;
import me.osm.gazetteer.striper.GeoJsonWriter;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.osm.gazetteer.ExternalSorting.ExternalSort;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.prep.PreparedGeometry;
import com.vividsolutions.jts.geom.prep.PreparedGeometryFactory;
import com.vividsolutions.jts.index.strtree.STRtree;

/**
 * Build boundaries hierarchy
 *
 * Load boundaries, for each boundary add upper boundaries
 * (boundaries with smaller admin_leve) which covers this
 * boundary.
 * */
public class JoinBoundariesExecutor {

	private static final Logger log = LoggerFactory
			.getLogger(JoinBoundariesExecutor.class);

	private File binxFile;
	private int skipedBoundaries = 0;

	private Map<String, BoundaryCortage> bhierarchy;

	private AddressesParser addressesParser;

	private static final AdmLvlComparator ADM_LVL_COMPARATOR = new AdmLvlComparator();

	private static class AdmLvlComparator implements Comparator<String> {

		private Set<Integer> lvls = new HashSet<>();

		@Override
		public int compare(String arg0, String arg1) {
			int i1 = arg0 == null ? -1 : Integer.parseInt(GeoJsonWriter
					.getAdmLevel(arg0));
			int i2 = arg1 == null ? -1 : Integer.parseInt(GeoJsonWriter
					.getAdmLevel(arg1));

			if (i1 > 0)
				lvls.add(i1);
			if (i2 > 0)
				lvls.add(i2);

			if (i1 == i2) {

				String id1 = GeoJsonWriter.getId(arg0);
				String id2 = GeoJsonWriter.getId(arg1);

				return id2.compareTo(id1);
			} else {
				return Integer.compare(i1, i2);
			}
		}

		public Set<Integer> getLvls() {
			return lvls;
		}

	}

	/**
	 * Calculate hierarchy, and call handler with results.
	 *
	 * @param stripesFolder
	 * 				Base folder with data
	 * @param common
	 * 				Add those JSONObject's to all results
	 * @param filter
	 * 				List of boundaries ids which should appear among
	 * 				upper boundaries to subject be sended to handler
	 * */
	public void run(String stripesFolder, List<JSONObject> common,
			Set<String> filter) {

		log.info("Run join boundaries, with filter [{}]",
				StringUtils.join(filter, ", "));

		long start = (new Date()).getTime();

		try {
			binxFile = FileUtils
					.withGz(new File(stripesFolder + "/binx.gjson"));

			if (binxFile.exists()) {
				joinBoundaries(stripesFolder, common, filter);
			} else {
				log.info("Skip boundaries index join");
			}

		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		log.info(
				"Join boundaries done in {}",
				DurationFormatUtils.formatDurationHMS(new Date().getTime()
						- start));
	}

	private void joinBoundaries(String stripesFolder, List<JSONObject> common,
			final Set<String> filter) throws Exception {

		addressesParser = Options.get().getAddressesParser();

		boolean distinct = false;

		BufferedReader binxReader = new BufferedReader(new InputStreamReader(
				FileUtils.getFileIS(binxFile)));
		List<File> l = ExternalSort.sortInBatch(binxReader,
				binxFile.length() * 10, ADM_LVL_COMPARATOR, 100,
				100 * 1024 * 1024, Charset.forName("UTF8"), new File(
						stripesFolder), distinct, 0, true);

		binxFile = new File(stripesFolder + "/" + "binx-sorted.gjson");
		int sorted = ExternalSort.mergeSortedFiles(l, binxFile,
				ADM_LVL_COMPARATOR, Charset.forName("UTF8"), distinct, false,
				true, null);

		log.info("{} boundaries was sorted", sorted);

		List<Integer> lvls = new ArrayList<Integer>(
				ADM_LVL_COMPARATOR.getLvls());
		Collections.sort(lvls);

		log.info("Admin levels: [{}]", StringUtils.join(lvls, ", "));

		final Map<Integer, List<BoundaryCortage>> boundariesByLevel = new LinkedHashMap<>();

		// Говно, приходится грузить все
		FileUtils.handleLines(binxFile, new FileUtils.LineHandler() {

			@Override
			public void handle(String s) {
				JSONObject obj = new JSONObject(s);
				JSONObject properties = obj.optJSONObject("properties");
				if(properties != null) {
					int lvl = properties.optInt("admin_level", -1);
					if(lvl > 0) {
						if(boundariesByLevel.get(lvl) == null) {
							boundariesByLevel.put(lvl, new ArrayList<BoundaryCortage>());
						}

						boundariesByLevel.get(lvl).add(new BoundaryCortage(obj));
					}
				}
			}

		});

		bhierarchy = new HashMap<String, BoundaryCortage>(sorted + 100);

		log.info("Boundaries loaded");
		int top = 0;
		for(Entry<Integer, List<BoundaryCortage>> layer : boundariesByLevel.entrySet()) {
			STRtree index = new STRtree();

			top = layer.getKey();
			for(Entry<Integer, List<BoundaryCortage>> entry : boundariesByLevel.entrySet()) {
				if(entry.getKey() > top && Math.abs(entry.getKey() - top) < 4) {
					for(BoundaryCortage b : entry.getValue()) {
						index.insert(new Envelope(b.getGeometry().getCentroid().getCoordinate()), b);
					}
				}
			}
			index.build();

			log.info("Fill down hierarcy from level {}. For {} uppers, and {} downs.",
					new Object[]{top, layer.getValue().size(), index.size()});

			for(BoundaryCortage up : layer.getValue()) {

				//we are in 4326
				PreparedGeometry preparedUpGeometry = PreparedGeometryFactory.prepare(up.getGeometry());
				PreparedGeometry preparedUpGeometryBuffer = null;

				// There are too many of admin_level=8 it's
				// faster to modify downlays geometry
				if(up.getProperties().optInt("admin_level", 20) < 8) {
					Envelope env = up.getGeometry().getEnvelopeInternal();
					double hypot = Math.hypot(env.getWidth(), env.getHeight());
					double buffer = -(hypot * 0.0001);
					preparedUpGeometryBuffer = PreparedGeometryFactory.prepare(up.getGeometry().buffer(buffer));
				}


				@SuppressWarnings("unchecked")
				List<BoundaryCortage> dwns = index.query(up.getGeometry().getEnvelopeInternal());
				for(BoundaryCortage dwn : dwns) {
					if(covers(up, preparedUpGeometry, preparedUpGeometryBuffer, dwn)) {
						if(bhierarchy.get(dwn.getId()) == null ) {
							bhierarchy.put(dwn.getId(), up.copyRef());
						}
						else if(bhierarchy.get(up.getId()) != null) {
							bhierarchy.put(dwn.getId(), up.copyRef());
						}
					}
				}
			}

			log.info("Done level {}", top);
		}

		FileUtils.handleLines(binxFile, new FileUtils.LineHandler() {

			@Override
			public void handle(String s) {
				JSONObject obj = new JSONObject(s);

				String id = obj.getString("id");
				List<JSONObject> uppers = asJsonRefs(getUppers(id));

				if(filter == null || filter.isEmpty() || checkById(obj, uppers, filter)) {
					obj.put("boundaries", addressesParser.boundariesAsArray(obj, uppers));

					handleOut(obj);
				}
				else {
					skipedBoundaries++;
				}
			}

		});

		log.info("{} boundaries skiped", skipedBoundaries);

		binxFile.delete();
	}

	private List<BoundaryCortage> getUppers(String id) {

		List<BoundaryCortage> result = new ArrayList<BoundaryCortage>();

		BoundaryCortage up = bhierarchy.get(id);

		while (up != null) {
			result.add(up);
			up = bhierarchy.get(up.getId());
		}

		return result;
	}

	private List<JSONObject> asJsonRefs(List<BoundaryCortage> uppers) {
		List<JSONObject> result = new ArrayList<JSONObject>();

		for(BoundaryCortage up : uppers) {
			JSONObject o = new JSONObject();

			String upid = up.getId();
			o.put("id", upid);
			o.put(GeoJsonWriter.PROPERTIES, up.getProperties());

			JSONObject meta = new JSONObject();
			String osmId = StringUtils.split(upid, '-')[2];
			meta.put("id", Long.parseLong(osmId.substring(1)));
			meta.put("type", osmId.charAt(0) == 'r' ? "relation" : "way");

			o.put(GeoJsonWriter.META, meta);

			result.add(o);
		}

		return result;
	}

	private boolean checkById(JSONObject obj, List<JSONObject> uppers,
			Set<String> filter) {

		if(checkById(obj, filter)) {
			return true;
		}

		for(JSONObject up : uppers) {
			if(checkById(up, filter)) {
				return true;
			}
		}

		return false;
	}

	private boolean checkById(JSONObject obj, Set<String> filter) {
		for(String fltr : filter) {
			if(StringUtils.contains(obj.getString("id"), fltr)) {
				return true;
			}
		}
		return false;
	}

	private boolean covers(BoundaryCortage up, PreparedGeometry preparedUpGeometry,
			PreparedGeometry preparedUpGeometryBuffer, BoundaryCortage dwn) {

		try {
			// fast
			if(preparedUpGeometry.covers(dwn.getGeometry())) {
				return true;
			}

			if(preparedUpGeometryBuffer == null) {
				Envelope env = dwn.getGeometry().getEnvelopeInternal();
				double hypot = Math.hypot(env.getWidth(), env.getHeight());
				double buffer = -(hypot * 0.005);

				return preparedUpGeometry.intersects(dwn.getGeometry().buffer(buffer));
			}
			else {
				return preparedUpGeometryBuffer.intersects(dwn.getGeometry());
			}

		}
		catch (Exception e) {
			return false;
		}

	}

	private void handleOut(JSONObject obj) {
		for (JoinOutHandler handler : Options.get().getJoinOutHandlers()) {
			handler.handle(obj, "binx.gjson");
		}
	}
}
