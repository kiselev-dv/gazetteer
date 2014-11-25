package me.osm.gazetter.join.util;

import me.osm.gazetter.striper.GeoJsonWriter;

import org.json.JSONObject;

import com.vividsolutions.jts.geom.Geometry;

public class BoundaryCortage {
	private String id;

	private JSONObject properties;
	private Geometry geometry;
	
	private BoundaryCortage() {
		
	}
	
	public BoundaryCortage(JSONObject obj) {
		
		id = obj.getString("id");
		
		JSONObject geomJSON = obj.getJSONObject(GeoJsonWriter.META).getJSONObject(GeoJsonWriter.FULL_GEOMETRY);
		if("MultiPolygon".equals(geomJSON.getString("type"))) {
			geometry = GeoJsonWriter.getMultiPolygonGeometry(geomJSON.getJSONArray(GeoJsonWriter.COORDINATES)); 
		}
		else {
			geometry = GeoJsonWriter.getPolygonGeometry(geomJSON.getJSONArray(GeoJsonWriter.COORDINATES));
		}
		
		properties = obj.getJSONObject(GeoJsonWriter.PROPERTIES);
	}
	
	public BoundaryCortage(String id) {
		this.id = id;
	}

	public void clear() {
		this.geometry = null;
	}

	public String getId() {
		return id;
	}

	public JSONObject getProperties() {
		return properties;
	}

	public Geometry getGeometry() {
		return geometry;
	}
	
	@Override
	public int hashCode() {
		return id.hashCode();
	}
	
	@Override
	public boolean equals(Object obj) {
		if(obj == null) {
			return false;
		}
		
		return hashCode() == obj.hashCode(); 
	}
	
	public BoundaryCortage copyRef() {
		BoundaryCortage res = new BoundaryCortage();
		res.id = this.id;
		res.properties = this.properties;
		return res;
	}
}