package me.osm.osmdoc.imports.osmcatalog;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;

public class DocPartNSMapper extends NamespacePrefixMapper {
	@Override
	public String getPreferredPrefix(String namespaceUri, String suggestion,
			boolean requirePrefix) {
		
		if(namespaceUri.equals(OsmDocGenerator.DOC_PART_NAMESPACE)) {
			return "";
		}
		
		return suggestion;
	}
}