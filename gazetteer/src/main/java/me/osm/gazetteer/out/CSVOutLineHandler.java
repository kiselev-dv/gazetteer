package me.osm.gazetteer.out;

import java.util.List;
import java.util.Map;

import org.json.JSONObject;

public interface CSVOutLineHandler {

	public boolean handle(List<Object> row, String ftype, JSONObject jsonObject,
			Map<String, JSONObject> mapLevels, JSONObject addrRow, Integer ai);

}
