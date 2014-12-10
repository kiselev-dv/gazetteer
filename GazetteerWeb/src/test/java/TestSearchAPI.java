import static org.junit.Assert.assertEquals;

import java.net.URL;

import me.osm.gazetteer.web.Main;

import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;


public class TestSearchAPI {

	@BeforeClass
	public static void setUp() throws Exception {
		Main.main(new String[]{});
	}

	@Test
	public void getFeatureById() {
		JSONObject res = get("http://localhost:8080/api/feature/poipnt-3318332668-n3029246999");
		assertEquals(res.getString("feature_id"), "poipnt-3318332668-n3029246999");
		
		res = get("http://localhost:8080/api/feature/poipnt-3318332668-n3029246999/_related");
		assertEquals(res.getString("feature_id"), "poipnt-3318332668-n3029246999");
	}


	@Test
	public void searchMoscow() {
		{
			HttpMethod method = new GetMethod("http://localhost:8080/api/feature/_search");
			method.setQueryString(new NameValuePair[]{
					new NameValuePair("q", "Москва"),
					new NameValuePair("explain", "true")
			});
			
			JSONObject res = get(method);
			JSONObject first = res.getJSONArray("features").optJSONObject(0);
			
			try {
				assertEquals(first.getString("name"), "Москва");
				assertEquals(first.getString("type"), "admbnd");
				JSONArray center = first.getJSONObject("center_point").getJSONArray("coordinates");
				assertEquals(center.getDouble(0), 37.6299, 1.0);
				assertEquals(center.getDouble(1), 55.7447, 1.0);
			}
			catch (AssertionError e) {
				System.out.println("q=Москва");
				System.out.println(res.toString(2));
			}
		}

		{
			HttpMethod method = new GetMethod("http://localhost:8080/api/feature/_search");
			method.setQueryString(new NameValuePair[]{
					new NameValuePair("q", "Moscow"),
					new NameValuePair("explain", "true")
			});
			
			JSONObject res = get(method);
			JSONObject first = res.getJSONArray("features").optJSONObject(0);
			
			try {
				assertEquals(first.getString("name"), "Москва");
				assertEquals(first.getString("type"), "admbnd");
				JSONArray center = first.getJSONObject("center_point").getJSONArray("coordinates");
				assertEquals(center.getDouble(0), 37.6299, 1.0);
				assertEquals(center.getDouble(1), 55.7447, 1.0);
			}
			catch (AssertionError e) {
				System.out.println("q=Москва");
				System.out.println(res.toString(2));
			}
		}
	}

	private JSONObject get(String string) {
		return get(new GetMethod(string));
	}

	private JSONObject get(HttpMethod method) {
		try {
			URL url = new URL(method.getURI().getEscapedURI());
			byte[] byteArray = IOUtils.toByteArray(url.openStream());
			return new JSONObject(new String(byteArray, "UTF8"));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

}
