package me.osm.gazetter.test;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.osm.gazetter.dao.WriteDao;
import me.osm.gazetter.striper.Slicer;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;

public class SlicerTest {

	private static GeometryFactory factory = new GeometryFactory();
	private static Slicer slicer = new Slicer("");
	private static WriteDao fakeWD = new FakeWriteDAO();
	private static Map<String, List<String>> results = new HashMap<String, List<String>>();
	
	private static final class FakeWriteDAO implements WriteDao {

		@Override
		public synchronized void write(String line, String key) throws IOException {
			if(results.get(key) == null) {
				results.put(key, new ArrayList<String>());
			}
			
			results.get(key).add(line);
		}

		@Override
		public void close() {
			//do nothing
		}
		
	}
	
	@Before
	public void cleanResults() {
		results.clear();
	}
	
	@BeforeClass
	public static void prepare() {
		try {
			Field wd = slicer.getClass().getDeclaredField("writeDAO");
			wd.setAccessible(true);
			wd.set(slicer, fakeWD);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void testAddrPoint() {
		slicer.handleAddrPoint(new HashMap<String, String>(), factory.createPoint(new Coordinate(55.0, 0)), getMeta());
		assertTrue(results.containsKey("stripe" + ((55 + 180) * 10) + ".gjson")); 
		
		Slicer.setFactor(10);
		slicer.handleAddrPoint(new HashMap<String, String>(), factory.createPoint(new Coordinate(22.0, 0)), getMeta());
		assertTrue(results.containsKey("stripe" + ((22 + 180) * 100) + ".gjson"));
	}
	
	@Test
	public void test() {
		//slicer.stripeBoundary();
	}

	@Test
	public void test2() {
		//slicer.stripe();
	}

	private JSONObject getMeta() {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("type", "adrpnt");
		return jsonObject;
	}

}
