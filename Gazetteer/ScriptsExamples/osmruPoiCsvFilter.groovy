import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import me.osm.gazetter.out.CSVOutLineHandler;


public class OSMRUCsvHandler implements CSVOutLineHandler {
	
	public boolean handle(List<Object> row, String ftype, JSONObject jsonObject,
		Map<String, JSONObject> mapLevels, JSONObject addrRow, Integer rowIndex) {

		return rowIndex == null || rowIndex == 0;
	}
	
}