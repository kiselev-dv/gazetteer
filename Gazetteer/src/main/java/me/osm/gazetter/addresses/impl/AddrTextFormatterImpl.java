package me.osm.gazetter.addresses.impl;

import static me.osm.gazetter.addresses.AddressesLevelsMatcher.ADDR_NAME;

import java.util.List;

import me.osm.gazetter.addresses.AddrTextFormatter;

import org.json.JSONObject;

public class AddrTextFormatterImpl implements AddrTextFormatter {

	@Override
	public String joinNames(List<JSONObject> addrJsonRow, JSONObject properties) {
		return simpleJoin(addrJsonRow);
	}

	@Override
	public String joinBoundariesNames(List<JSONObject> addrJsonRow) {
		return simpleJoin(addrJsonRow);
	}

	private String simpleJoin(List<JSONObject> addrJsonRow) {
		StringBuilder sb = new StringBuilder();
		
		for(JSONObject lvl : addrJsonRow) {
			sb.append(", ").append(lvl.getString(ADDR_NAME));
		}
		
		if(sb.length() > 2) {
			return sb.substring(2);
		}
		
		return null;
	}

}
