import me.osm.gazetter.out.CSVOutLineHandler;

import org.apache.commons.lang3.StringUtils;

import org.json.*;

public class OSMRUCsvHandler implements CSVOutLineHandler {

	public void handle(List<Object> row, String ftype, JSONObject jsonObject,
			Map<String, JSONObject> mapLevels, JSONObject addrRow) {
		row.add(3, addrRow.optString("index_name"));
	}
}