package me.osm.gazetteer.join;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import me.osm.gazetteer.Options;
import me.osm.gazetteer.addresses.NamesMatcher;
import me.osm.gazetteer.striper.JSONFeature;
import me.osm.gazetteer.utils.LocatePoint;
import me.osm.gazetteer.addresses.AddressesUtils;
import me.osm.gazetteer.striper.GeoJsonWriter;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LineString;

public class HighwayNetworksJoiner {

	private final NamesMatcher namesMatcher = Options.get().getNamesMatcher();
	private boolean dropHghNetGeometries;

	private static final String[] HGHNET_COPY_KEYS_GEOMETRY = new String[]{"id", "properties", "geometry"};
	private static final String[] HGHNET_COPY_KEYS_DROP_GEOMETRY = new String[]{"id", "properties"};

	private List<JSONObject> streets;
	private JoinSliceRunable jsr;

	public HighwayNetworksJoiner(List<JSONObject> streets, boolean dropHghNetGeometries, JoinSliceRunable father) {
		this.streets = streets;
		this.dropHghNetGeometries = dropHghNetGeometries;
		this.jsr = father;
	}

	/**
	 * Find unique streets' addresses
	 * @throws StreetNetworkJoinError
	 * */
	public void createStreetsNetworks() throws StreetNetworkJoinError {

		Map<Coordinate, List<JSONObject>> streetParts =
				Collections.synchronizedMap(new TreeMap<Coordinate, List<JSONObject>>());
		fillHghNetEndPointsToObjMap(streetParts);


		for (Entry<Coordinate, List<JSONObject>> entry : streetParts.entrySet()) {
			List<JSONObject> value = entry.getValue();
			Set<String> visited = new TreeSet<>();

			for (JSONObject segment : value) {
				List<JSONObject> thread = new LinkedList<>();
				fillHigHwaysThread(thread, segment, getEndPoints(segment), visited, streetParts);

				if(!thread.isEmpty()) {
					joinStreetsNet(thread);
				}
			}
		}
	}

	private void fillHghNetEndPointsToObjMap (
			Map<Coordinate, List<JSONObject>> streetParts) {

		Iterator<JSONObject> iterator = streets.iterator();
		while(iterator.hasNext()) {
			JSONObject jsonObject = iterator.next();

			JSONArray boundaries = jsonObject.optJSONArray("boundaries");
			if(boundaries != null) {
				for(int i = 0; i < boundaries.length(); i++) {
					JSONObject b = boundaries.getJSONObject(i);
					long bhash = b.getLong("boundariesHash");

					String[] keys = this.dropHghNetGeometries ?
							HGHNET_COPY_KEYS_DROP_GEOMETRY : HGHNET_COPY_KEYS_GEOMETRY;

					JSONFeature hghnet = new JSONFeature(jsonObject, keys);
					hghnet.put("boundariesHash", bhash);
					hghnet.put("boundaries", new JSONArray(Arrays.asList(b)));

					JSONArray coords = jsonObject.getJSONObject("geometry").getJSONArray("coordinates");
					if(coords.length() >= 2 ) {

						Coordinate first = new Coordinate(coords.getJSONArray(0).getDouble(0),
								coords.getJSONArray(0).getDouble(1)) ;

						Coordinate last = new Coordinate(
								coords.getJSONArray(coords.length() - 1).getDouble(0),
								coords.getJSONArray(coords.length() - 1).getDouble(1));

						hghnet.put("pointA", new JSONArray(new double[]{first.x, first.y}));
						hghnet.put("pointZ", new JSONArray(new double[]{last.x, last.y}));

						if(streetParts.get(first) == null) {
							streetParts.put(first, new ArrayList<JSONObject>());
						}
						if(streetParts.get(last) == null) {
							streetParts.put(last, new ArrayList<JSONObject>());
						}

						streetParts.get(first).add(hghnet);
						streetParts.get(last).add(hghnet);
					}
				}
			}

			iterator.remove();
		}
	}

	private void joinStreetsNet(List<JSONObject> streetsNetBunch) {

		if(!streetsNetBunch.isEmpty()) {

			Collections.sort(streetsNetBunch, JoinSliceRunable.BY_ID_COMPARATOR);

			JSONObject first = streetsNetBunch.get(0);
			JSONFeature hghnet = new JSONFeature(first);

			String name = commonName(streetsNetBunch);

			JSONObject cp = new JSONObject();
			cp.put("type", "Point");

			JSONObject geometryJSON = first.getJSONObject(GeoJsonWriter.GEOMETRY);
			LineString ls = GeoJsonWriter.getLineStringGeometry(
					geometryJSON.getJSONArray(GeoJsonWriter.COORDINATES));

			Coordinate c = new LocatePoint(ls, ls.getLength() * 0.5).getPoint();
			cp.put("lon", c.x);
			cp.put("lat", c.y);


			String nameHash = StringUtils.replaceChars(
					String.valueOf(name.hashCode()), '-', 'm');

			String bhash = StringUtils.replaceChars(
					String.valueOf(first.optInt("boundariesHash")), '-', 'm');

			String id = "hghnet" + "-" + bhash + "-" + nameHash;

			hghnet.put("id", id);
			hghnet.put("feature_id", id);
			hghnet.put("type", "hghnet");
			hghnet.put("ftype", "hghnet");

			hghnet.put("center_point", cp);

			hghnet.remove("full_geometry");
			hghnet.remove("geometry");

			JSONArray members = new JSONArray();
			JSONArray geometries = new JSONArray();
			for(JSONObject o : streetsNetBunch) {
				members.put(o.getString("id"));

				JSONObject g = o.getJSONObject(GeoJsonWriter.GEOMETRY);
				g.put("id", o.getString("id"));
				geometries.put(g);
			}

			hghnet.put("members", members);
			hghnet.put("geometries", geometries);

			GeoJsonWriter.addTimestamp(hghnet);
			GeoJsonWriter.addMD5(hghnet);

			jsr.handleOut(hghnet);
		}
	}

	private String commonName(List<JSONObject> streetsNetBunch) {
		String result = null;
		for(JSONObject obj : streetsNetBunch) {
			Map<String, String> names = AddressesUtils.filterNameTags(obj);
			String testName = StringUtils.lowerCase(names.get("name"));
			if(result == null) {
				result = testName;
			}
			else if(StringUtils.contains(result, testName)) {
				// result = result;
			}
			else if(StringUtils.contains(testName, result)) {
				result = testName;
			}
		}
		return result;
	}

	private void fillHigHwaysThread(List<JSONObject> thread,
			JSONObject segment, Coordinate[] endPoints, Set<String> visited,
			Map<Coordinate, List<JSONObject>> streetParts) {

		thread.add(segment);
		visited.add(segment.getString("id"));

		// for endpointA
		fillByNode(thread, segment, endPoints[0], endPoints, visited, streetParts,
				streetParts.get(endPoints[0]));

		// for endpointZ
		fillByNode(thread, segment, endPoints[1], endPoints, visited, streetParts,
				streetParts.get(endPoints[1]));

	}

	private void fillByNode(List<JSONObject> thread, JSONObject segment, Coordinate node,
			Coordinate[] endPoints, Set<String> visited,
			Map<Coordinate, List<JSONObject>> streetParts,
			List<JSONObject> joinedByNode) {

		for(JSONObject other : joinedByNode) {
			if(visited.add(other.getString("id"))) {
				if(doesHghWaysMathcByTags(segment, other)) {
					Coordinate[] otherEndPoints = getEndPoints(other);
					Coordinate newEndPoint = node.equals(otherEndPoints[0])
							? otherEndPoints[1] : otherEndPoints[0];

							fillHigHwaysThread(thread, other,
									new Coordinate[]{endPoints[0], newEndPoint}, visited, streetParts);
				}
			}
		}
	}

	private boolean doesHghWaysMathcByTags(JSONObject segment, JSONObject other) {
		return namesMatcher.doesStreetsMatch(
				AddressesUtils.filterNameTags(segment), AddressesUtils.filterNameTags(other));
	}

	private Coordinate[] getEndPoints(JSONObject segment) {
		return new Coordinate[]{
				new Coordinate(segment.getJSONArray("pointA").getDouble(0),
						segment.getJSONArray("pointA").getDouble(1)),
					new Coordinate(segment.getJSONArray("pointZ").getDouble(0),
							segment.getJSONArray("pointZ").getDouble(1))
		};
	}
}
