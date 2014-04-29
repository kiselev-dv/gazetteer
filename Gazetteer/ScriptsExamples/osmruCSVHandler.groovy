import me.osm.gazetter.out.CSVOutLineHandler;
import me.osm.gazetter.striper.GeoJsonWriter;

import org.apache.commons.lang3.StringUtils;
import org.json.*;

public class OSMRUCsvHandler implements CSVOutLineHandler {

	private static Map<String, String> codes = [
		"boundary:2":"country", 
		"boundary:4":"region", 
		"boundary:6":"district", 
		"place:city":"city", 
		"place:town":"city", 
		"place:village":"village", 
		"place:hamlet":"village", 
		"hghway":"street", 
		"adrpnt":"house"];

	private static Map<String, Integer> numbers = [
        "country":5, 
        "region":10, 
        "district":15, 
        "city":20, 
        "village":25, 
        "street":30, 
        "house":35];
	
	public boolean handle(List<Object> row, String ftype, JSONObject jsonObject,
			Map<String, JSONObject> mapLevels, JSONObject addrRow, Integer rowIndex) {

			String addrType = row.get(3);
			
			String osmruType = codes.get(addrType);
			if("admbnd".equals(row.get(2))) {
				osmruType = codes.get("boundary:" + 
					jsonObject.getJSONObject(GeoJsonWriter.PROPERTIES).optString("admin_level"));
				row.set(1, StringUtils.split (row.get(0), '-')[2]);
			}
			
			if(osmruType == null) {
				return false;
			}
			
			row.set(2, osmruType);
			row.set(3, numbers.get(osmruType));
			
			row.add(5, addrRow.optString("index_name"));
			
			return true;
	}
}