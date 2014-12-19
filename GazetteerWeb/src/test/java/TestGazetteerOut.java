import static org.junit.Assert.*;

import java.util.ArrayList;

import me.osm.gazetter.join.out_handlers.GazetteerOutWriter;
import me.osm.gazetter.join.out_handlers.JoinOutHandler;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


public class TestGazetteerOut {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void test() {
		JSONObject jsonObject = new JSONObject("{\"id\":\"poipnt-1545903732-n283644714\",\"ftype\":\"poipnt\",\"timestamp\":\"2014-12-19T01:25:55.145Z\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[14.72291050,50.82235920]},\"metainfo\":{\"id\":283644714,\"type\":\"node\"},\"poiTypes\":[\"information\"],\"properties\":{\"information\":\"guidepost\",\"source\":\"survey\",\"name\":\"Pod Hvozdem (rozcestí)\",\"tourism\":\"information\",\"hiking\":\"yes\",\"operator\":\"cz:KČT\"},\"type\":\"Feature\"}");
		
		JoinOutHandler handler = (new GazetteerOutWriter()).newInstance(new ArrayList<String>());
		handler.handle(jsonObject, "test");
	}

}
