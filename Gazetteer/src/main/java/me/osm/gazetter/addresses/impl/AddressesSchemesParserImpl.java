package me.osm.gazetter.addresses.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.osm.gazetter.addresses.AddressesSchemesParser;
import me.osm.gazetter.striper.JSONFeature;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AddressesSchemesParserImpl implements AddressesSchemesParser {
	
	private static final Logger log = LoggerFactory.getLogger(AddressesSchemesParserImpl.class);
	
	@Override
	public List<JSONObject> parseSchemes(JSONObject properties) {
		
		List<JSONObject> result = new ArrayList<>();
		
		// addr:...2
		if(properties.has("addr:housenumber2")) {
			
			JSONObject addr1 = JSONFeature.copy(properties);
			addr1.put(ADDR_SCHEME, "addr:hn2-1");
			result.add(addr1);

			String hn2 = properties.optString("addr:housenumber2");
			String street2 = properties.optString("addr:street2");
			
			JSONObject addr2 = new JSONObject(properties);
			if(StringUtils.isNotEmpty(hn2)) {
				addr2.put("addr:housenumber", hn2);
				addr2.put(ADDR_SCHEME, "addr:hn2-2");
				if(StringUtils.isNotEmpty(street2)) {
					addr2.put("addr:street", street2);
				}
				else if(addr2.has("addr:street")) {
					addr2.remove("addr:street");
					log.warn("Ambvalent addresses {}", properties);
				}
				
				result.add(addr2);
			}
		}
		//addr:hn=1/2 addr:street1 addr:street2
		else if(properties.has("addr:street2")) {
			String hn = properties.optString("addr:housenumber");

			String[] split = StringUtils.split(hn, "/\\;");
			
			if(split.length == 2) {
				String s1 = properties.optString("addr:street");
				String s2 = properties.optString("addr:street2");
				
				if(StringUtils.isEmpty(s1) || StringUtils.isEmpty(s2)) {
					return Collections.singletonList(properties); 
				}
				
				JSONObject addr1 = JSONFeature.copy(properties);
				addr1.put("addr:housenumber", split[0]);
				addr1.put("addr:hn-orig", hn);
				addr1.put(ADDR_SCHEME, "addr:street2-1");
				result.add(addr1);

				JSONObject addr2 = JSONFeature.copy(properties);
				addr2.put("addr:housenumber", split[1]);
				addr2.put("addr:street", s2);
				addr2.put("addr:hn-orig", hn);
				addr2.put(ADDR_SCHEME, "addr:street2-2");
				result.add(addr2);
			}
			else {
				result.add(properties);
			}
		}
		//AddrN
		//TODO: search for all addrN levels and Ns
		else if(properties.has("addr2:housenumber")) {
			JSONObject addr1 = JSONFeature.copy(properties);
			addr1.put(ADDR_SCHEME, "addrN-1");
			result.add(addr1);

			JSONObject addr2 = JSONFeature.copy(properties);
			addr2.put("addr:housenumber", properties.optString("addr2:housenumber"));
			addr2.put("addr:street", properties.optString("addr:street2"));
			addr2.put(ADDR_SCHEME, "addrN-2");
			result.add(addr2);
		}
		else {
			JSONObject addr1 = JSONFeature.copy(properties);
			addr1.put(ADDR_SCHEME, "regular");
			result.add(addr1);
		}
		
		return result;
	}
}
