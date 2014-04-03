package me.osm.gazetter.out;

import me.osm.gazetter.striper.FeatureTypes;
import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.utils.LocatePoint;

import org.json.JSONArray;
import org.json.JSONObject;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Polygon;

public class FeatureValueExctractorImpl implements FeatureValueExtractor {

	@Override
	public String getValue(String key, JSONObject jsonObject) {
		try {
			String ftype = jsonObject.getString("ftype");
			
			switch (key) {
			case "id":
				return jsonObject.getString("id");

			case "type":
				return ftype;
				
			case "osm-id":
				return String.valueOf(jsonObject.getJSONObject(GeoJsonWriter.META).getLong("id"));
				
			case "osm-type":
				return jsonObject.getJSONObject(GeoJsonWriter.META).getString("type");
				
			case "lon":
				if(FeatureTypes.HIGHWAY_FEATURE_TYPE.equals(ftype)) {
					LineString ls = GeoJsonWriter.getLineStringGeometry(
							jsonObject.getJSONObject(GeoJsonWriter.GEOMETRY)
								.getJSONArray(GeoJsonWriter.COORDINATES));
					Coordinate c = new LocatePoint(ls, 0.5).getPoint();
					return String.valueOf(c.x);
				}
				else {
					return String.valueOf(jsonObject.getJSONObject(GeoJsonWriter.GEOMETRY)
							.getJSONArray(GeoJsonWriter.COORDINATES).get(0));
				}

			case "lat":
				if(FeatureTypes.HIGHWAY_FEATURE_TYPE.equals(ftype)) {
					LineString ls = GeoJsonWriter.getLineStringGeometry(
							jsonObject.getJSONObject(GeoJsonWriter.GEOMETRY)
								.getJSONArray(GeoJsonWriter.COORDINATES));
					Coordinate c = new LocatePoint(ls, 0.5).getPoint();
					return String.valueOf(c.y);
				}
				else {
					return String.valueOf(jsonObject.getJSONObject(GeoJsonWriter.GEOMETRY)
							.getJSONArray(GeoJsonWriter.COORDINATES).get(1));
				}
			
			case "centroid":
				if(FeatureTypes.HIGHWAY_FEATURE_TYPE.equals(ftype)) {
					LineString ls = GeoJsonWriter.getLineStringGeometry(
							jsonObject.getJSONObject(GeoJsonWriter.GEOMETRY)
								.getJSONArray(GeoJsonWriter.COORDINATES));
					Coordinate c = new LocatePoint(ls, 0.5).getPoint();
					return "POINT (" + c.x + " " + c.y + ")";
				}
				else {
					JSONArray coords = jsonObject.getJSONObject(GeoJsonWriter.GEOMETRY)
							.getJSONArray(GeoJsonWriter.COORDINATES);
					
					return "POINT (" + coords.getDouble(0) + " " + coords.getDouble(1) + ")";
				}

			case "full-geometry":
				
				JSONObject fullGeometry = null;
				
				if(FeatureTypes.PLACE_POINT_FTYPE.equals(ftype)) {
					JSONObject matchedBoundary = jsonObject.optJSONObject("matchedBoundary");
					if(matchedBoundary != null) {
						fullGeometry = matchedBoundary.getJSONObject(GeoJsonWriter.META).optJSONObject(GeoJsonWriter.FULL_GEOMETRY);
					}
				}
				else {
					JSONObject meta = jsonObject.getJSONObject(GeoJsonWriter.META);
					fullGeometry = meta.optJSONObject("fullGeometry");
				}
				
				if(fullGeometry != null && "Polygon".equals(fullGeometry.optString("type"))) {
					Polygon polygon = GeoJsonWriter.getPolygonGeometry(fullGeometry.getJSONArray("coordinates"));
					return polygon.toString();
				}
				else if(fullGeometry != null && "LineString".equals(fullGeometry.optString("type"))) {
					LineString ls = GeoJsonWriter.getLineStringGeometry(fullGeometry.getJSONArray("coordinates"));
					return ls.toString();
				}
				
				break;
				
			case "nearest:city":
				jsonObject.getJSONObject("nearestCity").getJSONObject(GeoJsonWriter.PROPERTIES).optString("name");
				
			case "nearest:city:id":
				jsonObject.getJSONObject("nearestCity").getJSONObject(GeoJsonWriter.PROPERTIES).optString("id");
				
			case "nearest:neighbourhood":
				jsonObject.getJSONObject("nearestNeighbour").getJSONObject(GeoJsonWriter.PROPERTIES).optString("name");
			
			case "nearest:neighbourhood:id":	
				jsonObject.getJSONObject("nearestNeighbour").getJSONObject(GeoJsonWriter.PROPERTIES).optString("id");
				
			}
		}
		catch (Exception e) {
			return null;
		}
		
		return null;
	}
}
