import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.groovy.util.StringUtil;
import org.json.JSONObject;

import me.osm.gazetter.out.CSVOutLineHandler;


public class OSMRUCsvHandler implements CSVOutLineHandler {
	
	public boolean handle(List<Object> row, String ftype, JSONObject jsonObject,
		Map<String, JSONObject> mapLevels, JSONObject addrRow, Integer rowIndex) {

		if(rowIndex == null || rowIndex == 0) {
			
			String name = row.get(2);
			String clazz = row.get(4);
			String addrIndex = addrRow.optString("index_name");
			
			StringBuilder sb = new StringBuilder();
			if(StringUtils.isNotBlank(name)) {
				sb.append(", ").append(name);
			}
			if(StringUtils.isNotBlank(clazz)) {
				sb.append(", ").append(clazz);
			}
			if(StringUtils.isNotBlank(addrIndex)) {
				sb.append(", ").append(addrIndex);
			}
			
			row.add(16, sb.length() > 2 ? sb.substring(2) : "");
			
			return true;
		}
		
		return false;
	}
	
}