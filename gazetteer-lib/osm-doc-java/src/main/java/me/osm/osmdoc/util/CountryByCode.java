package me.osm.osmdoc.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

@SuppressWarnings("unchecked")
public class CountryByCode {
	
	private static final Map<String, String> map = new HashMap<String, String>();

	static{
		try {
			for(String s : (List<String>)IOUtils.readLines(CountryByCode.class.getResourceAsStream("/cc.txt"))) {
				String[] split = StringUtils.splitByWholeSeparator(s, " - ");
				map.put(split[0], split[1]);
			} 
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static String get(String key) {
		return map.get(key);
	}
}
