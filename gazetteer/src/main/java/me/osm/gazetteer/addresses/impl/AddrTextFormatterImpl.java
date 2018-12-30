package me.osm.gazetteer.addresses.impl;

import static me.osm.gazetteer.addresses.AddressesLevelsMatcher.ADDR_NAME;

import java.util.List;

import me.osm.gazetteer.addresses.AddrTextFormatter;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

/**
 * Default implementation for
 * {@link AddrTextFormatter}
 * */
public class AddrTextFormatterImpl implements AddrTextFormatter {

	@Override
	public String joinNames(List<JSONObject> addrJsonRow, JSONObject properties, String lang) {
		return simpleJoin(addrJsonRow, lang);
	}

	@Override
	public String joinBoundariesNames(List<JSONObject> addrJsonRow, String lang) {
		return simpleJoin(addrJsonRow, lang);
	}

	private String simpleJoin(List<JSONObject> addrJsonRow, String lang) {

		StringBuilder sb = new StringBuilder();
		for(JSONObject lvl : addrJsonRow) {

			String string = null;

			if(lang == null) {
				string = lvl.getString(ADDR_NAME);
			}
			else {
				JSONObject names = lvl.optJSONObject("names");
				if(names != null) {
					String translated = names.optString("name:" + lang);
					if(StringUtils.isNoneBlank(translated)) {
						string = translated;
					}
					else {
						string = lvl.getString(ADDR_NAME);
					}
				}
				else {
					string = lvl.getString(ADDR_NAME);
				}
			}

			if(StringUtils.isNotBlank(string)) {
				sb.append(", ").append(string);
			}
		}

		if(sb.length() > 2) {
			return sb.substring(2);
		}

		return null;
	}

}
