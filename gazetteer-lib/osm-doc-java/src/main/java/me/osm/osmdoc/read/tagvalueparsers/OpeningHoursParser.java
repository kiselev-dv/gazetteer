package me.osm.osmdoc.read.tagvalueparsers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public class OpeningHoursParser implements TagValueParser {

	
	private static final String[] DAYS_ARRAY = new String[]{"MO", "TU", "WE", "TH", "FR", "SA", "SU"};
	
	private static final Set<String> DAYS = new HashSet<String>(Arrays.asList(DAYS_ARRAY));

	@Override
	public Object parse(String rawValue) {
		
		rawValue = rawValue.toUpperCase();
		
		JSONObject result = new JSONObject();
		
		if(rawValue.equals("24/7")) {
			result.put("24_7", true);
			return result;
		}

		if(rawValue.equals("sunrise-sunset")) {
			result.put("sunrise_sunset", true);
			return result;
		}

		//say hello to russian post :)
		//Mo-Fr 08:00-13:00,14:00-20:00; Sa 09:00-13:00,14:00-18:00; Su off
		//09:00-21:00
		//Mo-Su␣09:00-21:00
		//Tu-Fr 09:00-14:00,15:00-17:00; Sa 09:00-14:00,15:00-16:00; Mo,Su off
		//Mo-Tu,Th-Fr 09:00-13:00,14:00-17:00; We 14:00-17:00
		//Mo,We-Su 12:00-18:00; Tu off
		
//		String[] days = rawValue.split("(,|;|\\s)\\s*(?=(Mo|Tu|We|Th|Fr|Sa|Su))");
		String[] days = rawValue.split("(?<=(\\d))(,|;|\\s)\\s*(?=(MO|TU|WE|TH|FR|SA|SU))");

		for(String byDay : days) {
			byDay = StringUtils.strip(byDay);
			if(byDay.length() > 2) {
				
				String day = byDay.substring(0, 2);
				
				if (DAYS.contains(day)) {
					
					String[] split = StringUtils.split(byDay, ' ');
					if(split.length < 2) {
						return null;
					}
					
					String[] daysOfWeek = getDays(split[0]);
					
					if(daysOfWeek == null || daysOfWeek.length == 0) {
						return null;
					}
					
					String hoursString = StringUtils.remove(byDay, split[0]);
					
					if(hoursString.toLowerCase().contains("off")) {
						continue;
					}
					
					int[] hours = parseHours(hoursString);
					if(hours == null) {
						return null;
					}
						
					for(String d : daysOfWeek) {
						result.put(d, new JSONArray(hours));
					}
				}
				//just time
				else {
					int[] hours = parseHours(byDay);
					if(hours == null) {
						return null;
					}
					
					for(String d : DAYS_ARRAY) {
						result.put(d, new JSONArray(hours));
					}
				}
			}
			
		}
		
		return result;
	}

	private String[] getDays(String string) {
		List<String> result = new ArrayList<String>(7);

		String[] parts = StringUtils.split(string, ",;");
		for(String prt: parts) {
			String part = StringUtils.strip(prt);
			
			if(part.contains("-")) {
				String[] split = StringUtils.split(part, '-');
				
				if(split.length != 2) {
					continue;
				}
				
				int firstIndex = ArrayUtils.indexOf(DAYS_ARRAY, split[0]);
				int lastIndex = ArrayUtils.indexOf(DAYS_ARRAY, split[1]);
				
				if(firstIndex == ArrayUtils.INDEX_NOT_FOUND || lastIndex == ArrayUtils.INDEX_NOT_FOUND ){
					return null;
				}
				
				
				int i = firstIndex;
				while(true) {
					result.add(DAYS_ARRAY[i]);
					
					if(i == lastIndex) {
						break;
					}
					
					i++;
					if(i == DAYS_ARRAY.length) {
						i = 0;
					}
				}
			}
			else {
				if(DAYS.contains(part)) {
					result.add(part);
				}
			}
		}
		
		if(!result.isEmpty()) {
			return result.toArray(new String[result.size()]);
		}
		
		
		return null;
	}

	private int[] parseHours(String string) {
		
		List<Integer> result = new ArrayList<Integer>();
		
		String[] periods = StringUtils.split(string, ",;");
		for(String period : periods) {
			period = StringUtils.strip(period);
			
			for(String s : StringUtils.split(period, '-')) {
				s = StringUtils.strip(s);
				try {
					String[] hm = StringUtils.split(s, ':');
					result.add(Integer.parseInt(hm[0]));
				}
				catch (NumberFormatException e) {
					
				}
			}
		}
		
		if(result.size() == 1 && 24 == result.iterator().next()) {
			return new int[]{0, 24};
		}
		
		if(result.size() > 0 && result.size() % 2 == 0) {
			return ArrayUtils.toPrimitive(result.toArray(new Integer[result.size()]));
		}
		
		return null;
	}

}
