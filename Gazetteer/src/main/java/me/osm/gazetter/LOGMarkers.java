package me.osm.gazetter;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class LOGMarkers {

	public static final Marker E_INTRPLTN_NO_ADDR_POINT 
		= MarkerFactory.getMarker("E_INTRPLTN_NO_ADDR_ON_POINT");

	public static final Marker E_INTRPLTN_DIF_STREETS 
		= MarkerFactory.getMarker("E_INTRPLTN_DIF_STREETS");

	//----------------------------------------------------------
	
	public static final Marker E_NO_POINTS_FOR_RELATION 
		= MarkerFactory.getMarker("E_NO_POINTS_FOR_RELATION"); 
	
	public static final Marker E_INTRPLTN_UNSPRTED_TYPE 
		= MarkerFactory.getMarker("E_INTRPLTN_UNSPRTED_TYPE");
	
	public static final Marker E_NO_POINTS_FOR_WAY 
		= MarkerFactory.getMarker("E_NO_POINTS_FOR_WAY");
	
	public static final Marker E_WRONG_NUM_OF_POINTS 
		= MarkerFactory.getMarker("E_WRONG_NUM_OF_POINTS");

	public static final Marker E_POLYGON_BUID_ERR 
		= MarkerFactory.getMarker("E_POLYGON_BUID_ERR");
	
}
