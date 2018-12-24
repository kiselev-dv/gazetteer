package me.osm.gazetteer.addresses.impl;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import me.osm.gazetteer.addresses.AddressesUtils;
import me.osm.gazetteer.addresses.NamesMatcher;

import org.apache.commons.lang3.StringUtils;

/**
 * Default implementation for
 * {@link NamesMatcher}
 * */
public class NamesMatcherImpl implements NamesMatcher {

	@Override
	public boolean isPlaceNameMatch(String name, Set<String> names){
		return names.contains(name);
	}

	@Override
	public boolean isStreetNameMatch(String street,
			Map<String, String> filterNameTags) {

		return new HashSet<>(filterNameTags.values()).contains(street);
	}

	@Override
	public boolean isPlaceNameMatch(String name,
			Map<String, String> filterNameTags) {

		return new HashSet<>(filterNameTags.values()).contains(name);
	}

	@Override
	public boolean doesStreetsMatch(Map<String, String> o1names,
			Map<String, String> o2names) {

		String name1 = AddressesUtils.foldASCII(StringUtils.stripToEmpty(o1names.get("name")).toLowerCase());
		String name2 = AddressesUtils.foldASCII(StringUtils.stripToEmpty(o2names.get("name")).toLowerCase());

		return name1.contains(name2) || name2.contains(name1) ||
				o2names.values().contains(name1) || o1names.values().contains(name2);
	}

}
