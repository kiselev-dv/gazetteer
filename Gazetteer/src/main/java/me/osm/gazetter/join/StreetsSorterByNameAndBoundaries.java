package me.osm.gazetter.join;

import java.util.Comparator;
import java.util.Map;

import me.osm.gazetter.Options;
import me.osm.gazetter.addresses.AddressesUtils;
import me.osm.gazetter.addresses.NamesMatcher;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

public final class StreetsSorterByNameAndBoundaries implements
			Comparator<JSONObject> {
		
		private static final NamesMatcher namesMatcher = Options.get().getNamesMatcher();
		
		public static final StreetsSorterByNameAndBoundaries INSTANCE = new StreetsSorterByNameAndBoundaries();
		
		@Override
		public int compare(JSONObject o1, JSONObject o2) {
			int bhash1 = o1.optInt("boundariesHash");
			int bhash2 = o2.optInt("boundariesHash");

			if(bhash1 == bhash2) {
				Map<String, String> nt1 = AddressesUtils.filterNameTags(o1);
				Map<String, String> nt2 = AddressesUtils.filterNameTags(o2);
				if(namesMatcher.doesStreetsMatch(nt1, nt2)) {
					return 0;
				}
				
				String n1 = StringUtils.stripToEmpty(nt1.get("name"));
				String n2 = StringUtils.stripToEmpty(nt2.get("name"));
				
				return n1.compareTo(n2);
			}
			
			return Integer.compare(bhash1, bhash2);
		}
	}