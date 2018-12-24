package me.osm.gazetteer.test;

import static org.junit.Assert.assertEquals;
import me.osm.gazetteer.striper.GeoJsonWriter;

import org.junit.Test;

public class TimestampsTest {

	private static final String TEST1 = "{\"id\":\"plcpnt-3965682819-n1410323545\",\"ftype\":\"plcpnt\",\"timestamp\":\"2014-03-11T11:47:54.582Z\",\"properties\":{\"name\":\"Языково\",\"place\":\"village\"},\"type\":\"Feature\",\"metainfo\":{\"id\":1410323545,\"type\":\"node\"},\"geometry\":{\"type\":\"Point\",\"coordinates\":[52.04044540,53.27274440]}}";
	private static final String TEST2 = "{\"id\":\"adrpnt-0425079167-w90213286\",\"ftype\":\"adrpnt\",\"timestamp\":\"2014-03-12T08:47:27.354Z\",\"properties\":{\"building\":\"yes\",\"source\":\"bing\",\"addr:street\":\"улица Ленина\",\"addr:city\":\"Уфа\",\"addr:housenumber\":\"81\"},\"type\":\"Feature\",\"metainfo\":{\"id\":90213286,\"type\":\"way\"},\"geometry\":{\"type\":\"Point\",\"coordinates\":[55.95610440,54.74248220]}}";

	@Test
	public void test() {
		assertEquals("plcpnt-3965682819-n1410323545", GeoJsonWriter.getId(TEST1));
		GeoJsonWriter.getTimestamp(TEST1);
		GeoJsonWriter.getTimestamp(TEST2);
	}

}
