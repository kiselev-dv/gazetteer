package me.osm.gazetter.test;

import static org.junit.Assert.assertEquals;
import me.osm.gazetter.striper.GeoJsonWriter;

import org.junit.Test;

public class TimestampsTest {

	private static final String TEST = "{\"id\":\"plcpnt-3965682819-n1410323545\",\"ftype\":\"plcpnt\",\"timestamp\":\"2014-03-11T11:47:54.582Z\",\"properties\":{\"name\":\"Языково\",\"place\":\"village\"},\"type\":\"Feature\",\"metainfo\":{\"id\":1410323545,\"type\":\"node\"},\"geometry\":{\"type\":\"Point\",\"coordinates\":[52.04044540,53.27274440]}}";
	
	@Test
	public void test() {
		assertEquals("plcpnt-3965682819-n1410323545", GeoJsonWriter.getId(TEST));
		GeoJsonWriter.getTimestamp(TEST);
	}

}
