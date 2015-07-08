package me.osm.gazetter.addresses.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import me.osm.gazetter.addresses.AddressesSchemesParser;
import me.osm.gazetter.striper.JSONFeature;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation for
 * {@link AddressesSchemesParser}
 * */
public class AddressesSchemesParserImpl implements AddressesSchemesParser {
	
	private static final Logger log = LoggerFactory.getLogger(AddressesSchemesParserImpl.class);
	
	@SuppressWarnings("unchecked")
	@Override
	public List<JSONObject> parseSchemes(JSONObject properties) {
		
		List<JSONObject> result = new ArrayList<>();
		
		// addr:street  addr:housenumber
		// addr:street2 addr:housenumber2
		if(properties.has("addr:housenumber2")) {
			
			JSONObject addr1 = JSONFeature.copy(properties);
			addr1.put(ADDR_SCHEME, "addr:hn2.1");
			result.add(addr1);

			String hn2 = properties.optString("addr:housenumber2");
			String street2 = properties.optString("addr:street2");
			
			JSONObject addr2 = new JSONObject(properties);
			if(StringUtils.isNotEmpty(hn2)) {
				addr2.put("addr:housenumber", hn2);
				addr2.put(ADDR_SCHEME, "addr:hn2.2");
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
				addr1.put(ADDR_SCHEME, "addr:street2.1");
				result.add(addr1);

				JSONObject addr2 = JSONFeature.copy(properties);
				addr2.put("addr:housenumber", split[1]);
				addr2.put("addr:street", s2);
				addr2.put("addr:hn-orig", hn);
				addr2.put(ADDR_SCHEME, "addr:street2.2");
				result.add(addr2);
			}
			else {
				result.add(properties);
			}
		}
		
		//AddrN
		else if(properties.has("addr2:housenumber")) {
			
			JSONObject addr1 = JSONFeature.copy(properties);
			addr1.put(ADDR_SCHEME, "addrN.1");
			result.add(addr1);

			for(int i = 2;;i++) {
				
				JSONObject addrN = new JSONObject();
				String pref = "addr" + i;
				
				if(properties.has(pref + ":housenumber")) {
					for(String key : (Set<String>)properties.keySet()) {
						
						if(!key.startsWith("addr")) {
							addrN.put(key, properties.get(key));
						}
						
						if (key.startsWith(pref)) {
							addrN.put(key.replace(pref, "addr"), properties.get(key));
							addrN.remove(key);
						}
					}
					
					addrN.put(ADDR_SCHEME, "addrN." + i);
					result.add(addrN);
				}
				else {
					break;
				}
			}
			
			result = searchHNn(result);
		}
		
		// Conscription numbers 
		else if(properties.has("addr:conscriptionnumber") || properties.has("addr:streetnumber")) {

			String original = properties.optString("addr:housenumber");

			if(StringUtils.isNotBlank(properties.optString("addr:streetnumber"))) {
				JSONObject addr1 = JSONFeature.copy(properties);
				addr1.put("addr:housenumber", properties.optString("addr:streetnumber"));
				addr1.put("addr:hn-orig", original);
				addr1.put(ADDR_SCHEME, "addr:streetnumber");
				result.add(addr1);
			}

			if(StringUtils.isNotBlank(properties.optString("addr:conscriptionnumber"))) {
				JSONObject addr2 = JSONFeature.copy(properties);
				addr2.put("addr:housenumber", properties.optString("addr:conscriptionnumber"));
				addr2.put("addr:hn-orig", original);
				addr2.put(ADDR_SCHEME, "addr:conscriptionnumber");
				result.add(addr2);
			}
			
		}
		else {
			JSONObject addr1 = JSONFeature.copy(properties);
			addr1.put(ADDR_SCHEME, "regular");
			result.add(addr1);
		}
		
		return result;
	}

	
	//Say hello to SviMik 
	private List<JSONObject> searchHNn(List<JSONObject> list) {
		
		List<JSONObject> result = new ArrayList<JSONObject>();
		
		for(JSONObject obj : list) {
			if(obj.has("addr:housenumber2")) {
				
				JSONObject addr = JSONFeature.copy(obj);
				result.add(addr);

				for(int i = 2; true; i++) {
					if(obj.has("addr:housenumber" + i)) {
						addr = JSONFeature.copy(obj);
						addr.put("addr:housenumber", obj.get("addr:housenumber" + i));
					}
					else {
						break;
					}
				}
			}
			else {
				result.add(obj);
			}
		}
		
		return result;
	}
}
