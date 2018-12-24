package me.osm.gazetteer.join;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.osm.gazetteer.striper.GeoJsonWriter;

import org.json.JSONArray;
import org.json.JSONObject;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

public class PoiAddrJoinBuilder {

	private static final GeometryFactory factory = new GeometryFactory();

	public BestFitAddresses join(JSONObject poi, List<JSONObject> addresses) {

		return getBestAddress(poi, addresses);

	}

	public static class BestFitAddresses {
		// Same source - it's when we have poi tags and addr tags on a same geometry
		public JSONObject sameSource;

		// Nearest addr point
		public JSONObject nearest;

		// Poi contains addr points or geometry with addr tags contains poi
		public Set<JSONObject> contains;

		// Represent situation when poi point is a part of bulding way (poi is on entrance)
		// and other entrances have their own addresses
		public Set<JSONObject> shareBuildingWay;

		// Near the same as shareBuildingWay but poi point is inside building
		// and different entrances have different addresses.
		// In some regions poi address will have address
		// with all shared entrances house numbers range like
		// hn4-hn9, SomeStreet
		// Say hello to Kaliningrad (Kenigsberg).
		public Set<JSONObject> nearestShareBuildingWay;

		public JSONObject asJSON() {
			JSONObject obj = new JSONObject();

			obj.put("sameSource", PoiAddrJoinBuilder.asAddrRefer(sameSource));
			obj.put("nearest", PoiAddrJoinBuilder.asAddrRefer(nearest));
			obj.put("contains", PoiAddrJoinBuilder.asAddrRefers(contains));
			obj.put("shareBuildingWay", PoiAddrJoinBuilder.asAddrRefers(shareBuildingWay));
			obj.put("nearestShareBuildingWay", PoiAddrJoinBuilder.asAddrRefers(nearestShareBuildingWay));

			return  obj;
		}
	}

	private BestFitAddresses getBestAddress(JSONObject poi, List<JSONObject> addresses) {


		BestFitAddresses result = new BestFitAddresses();
		result.contains = new LinkedHashSet<>();

		JSONArray coords = poi.getJSONObject(GeoJsonWriter.GEOMETRY).getJSONArray(GeoJsonWriter.COORDINATES);
		final Coordinate pc = new Coordinate(coords.getDouble(0), coords.getDouble(1));

		JSONObject poiMeta = poi.getJSONObject(GeoJsonWriter.META);

		Point poiPointGeometry = factory.createPoint(pc);

		Collections.sort(addresses, new Comparator<JSONObject>() {
			@Override
			public int compare(JSONObject o1, JSONObject o2) {
				JSONArray coords1 = o1.getJSONObject(GeoJsonWriter.GEOMETRY).getJSONArray(GeoJsonWriter.COORDINATES);
				JSONArray coords2 = o2.getJSONObject(GeoJsonWriter.GEOMETRY).getJSONArray(GeoJsonWriter.COORDINATES);

				Coordinate pc1 = new Coordinate(coords1.getDouble(0), coords1.getDouble(1));
				Coordinate pc2 = new Coordinate(coords2.getDouble(0), coords2.getDouble(1));

				return Double.compare(pc.distance(pc1), pc.distance(pc2));
			}
		});

		Polygon poiOrigignalPolygon = getOriginalGeometry(poi);

		Map<Long, List<JSONObject>> building2Address = new HashMap<Long, List<JSONObject>>();
		result.shareBuildingWay = new LinkedHashSet<>();

		// Share one building way
		Long poiSharedBuildingWay = null;
		JSONObject poiBndg = poi.optJSONObject("bndgWay");
		if(poiBndg != null) {
			poiSharedBuildingWay = poiBndg.getLong("id");
		}

		List<JSONObject> tenNearest = addresses.subList(0, Math.min(10, addresses.size()));
		for(JSONObject addrOBJ : tenNearest) {

			JSONObject addrMeta = addrOBJ.getJSONObject(GeoJsonWriter.META);
			if(isSameSource(poiMeta, addrMeta)) {
				result.sameSource = addrOBJ;
				break;
			}

			if("way".equals(addrMeta.getString("type"))
					&& poiSharedBuildingWay != null
					&& poiSharedBuildingWay.equals(addrMeta.getLong("id"))) {
				result.shareBuildingWay.add(addrOBJ);

				//It's possible when poi point share way with two addressable buildings
				//so didn't break cycle here.
			}

			JSONArray addrCoords = addrOBJ.getJSONObject(GeoJsonWriter.GEOMETRY)
					.getJSONArray(GeoJsonWriter.COORDINATES);

			Coordinate ac = new Coordinate(addrCoords.getDouble(0), addrCoords.getDouble(1));
			Point addrPointGeometry = factory.createPoint(ac);

			// Search for addr points inside poi polygon
			if(poiOrigignalPolygon != null && poiOrigignalPolygon.contains(addrPointGeometry)) {
				result.contains.add(addrOBJ);
			}

			// Search for addr building polygon around poi
			Polygon addrOriginalPolygon = getOriginalGeometry(addrOBJ);
			if(addrOriginalPolygon != null && addrOriginalPolygon.contains(poiPointGeometry)) {
				result.contains.add(addrOBJ);

				// POI point inside 2 or more buildings?
				// I don't think it's normal situation.
				break;
			}

			JSONObject bndg = addrOBJ.optJSONObject("bndgWay");
			if(bndg != null) {
				long bid = bndg.getLong("id");
				if(building2Address.get(bid) == null) {
					building2Address.put(bid, new ArrayList<JSONObject>());
				}

				building2Address.get(bid).add(addrOBJ);
			}
		}

		//Big poi with addr point(s) on entrances
		if("way".equals(poiMeta.getString("type"))) {
			long poiWayId = poiMeta.getLong("id");
			List<JSONObject> shareWayAddresses = building2Address.get(poiWayId);
			if(shareWayAddresses != null) {
				result.shareBuildingWay.addAll(shareWayAddresses);
			}
		}

		//Poi is on aone of the entrances and other entrances have their own addresses.
		if(poiSharedBuildingWay != null) {
			List<JSONObject> shareWayAddresses = building2Address.get(poiSharedBuildingWay);
			if(shareWayAddresses != null) {
				result.shareBuildingWay.addAll(shareWayAddresses);
			}
		}

		//Nearest
		if(!addresses.isEmpty()) {

			result.nearest = addresses.get(0);

			//nearestShareBuildingWay
			JSONObject nearestB = result.nearest.optJSONObject("bndgWay");
			if(nearestB != null) {
				long bid = nearestB.getLong("id");
				List<JSONObject> sharedBndng = building2Address.get(bid);
				if(sharedBndng != null) {
					result.nearestShareBuildingWay = new LinkedHashSet<>(sharedBndng);
				}
			}
		}

		return result;
	}

	public static JSONArray asAddrRefers(
			Set<JSONObject> source) {

		JSONArray result = new JSONArray();

		if(source != null) {
			for(JSONObject obj : source) {
				result.put(asAddrRefer(obj));
			}
		}

		return result;
	}

	public static JSONObject asAddrRefer(JSONObject source) {

		if(source == null) {
			return null;
		}

		return new JSONObject(source, new String[]{"id", "meta", "ftype", "addresses", "properties"});
	}

	private boolean isSameSource(JSONObject poiMeta, JSONObject addrMeta) {
		return poiMeta.getString("type").equals(addrMeta.getString("type")) && poiMeta.getLong("id") == addrMeta.getLong("id");
	}

	private Polygon getOriginalGeometry(JSONObject obj) {
		JSONObject meta = obj.getJSONObject(GeoJsonWriter.META);
		JSONObject fullGeometry = meta.optJSONObject(GeoJsonWriter.FULL_GEOMETRY);

		if(fullGeometry != null && "Polygon".equals(fullGeometry.optString("type"))) {
			Polygon polygon = GeoJsonWriter.getPolygonGeometry(fullGeometry.getJSONArray("coordinates"));
			if(polygon.isValid()) {
				return polygon;
			}
		}

		return null;
	}

}
