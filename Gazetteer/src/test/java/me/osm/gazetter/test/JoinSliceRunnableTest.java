package me.osm.gazetter.test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import me.osm.gazetter.join.AddrJointHandler;
import me.osm.gazetter.join.JoinSliceRunable;

import org.json.JSONObject;
import org.junit.Test;

public class JoinSliceRunnableTest {
	
	@Test
	public void test1() {
		
		AddrJointHandler voidHandler = new AddrJointHandler(){

			@Override
			public JSONObject handle(JSONObject addrPoint,
					List<JSONObject> polygons,
					List<JSONObject> nearbyStreets,
					JSONObject nearestPlace,
					JSONObject nearesNeighbour,
					JSONObject associatedStreet) {
				
				return null;
			}

		};
		
		JoinSliceRunable joinSliceRunable = new JoinSliceRunable(
				voidHandler, 
				new File("/opt/osm/gazetteer/Gazetteer/src/test/resources/stripe1846.gjson.gz"), 
				new ArrayList<JSONObject>(), 
				new HashSet<String>(), 
				null, 
				null);
		
		joinSliceRunable.run();
	}
}
