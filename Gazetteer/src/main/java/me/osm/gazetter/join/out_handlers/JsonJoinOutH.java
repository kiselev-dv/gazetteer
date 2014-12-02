package me.osm.gazetter.join.out_handlers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import me.osm.osmdoc.model.Feature;
import me.osm.osmdoc.read.DOCFileReader;
import me.osm.osmdoc.read.DOCFolderReader;
import me.osm.osmdoc.read.DOCReader;
import me.osm.osmdoc.read.OSMDocFacade;
import me.osm.osmdoc.read.tagvalueparsers.TagsStatisticCollector;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import org.json.JSONArray;
import org.json.JSONObject;


public class JsonJoinOutH extends AddressPerRowJOHBase {

	public static final String NAME = "out-json";
	private List<Locale> translateTo = new ArrayList<Locale>();
	private DOCReader reader;
	private OSMDocFacade osmDocFacade;
	
	private static final TagsStatisticCollector tsc = new TagsStatisticCollector();
	
	@Override
	public JoinOutHandler newInstance(List<String> options) {
		
		parseArguments(options);
		
		
		return this;
	}

	protected void parseArguments(List<String> options) {
		
		ArgumentParser parser = ArgumentParsers.newArgumentParser(NAME);
		
		parser.addArgument("out=").setDefault("-").nargs("?");
		parser.addArgument("translate-to=").nargs("+").required(false);
		parser.addArgument("poi-catalog=").nargs("?");
		parser.addArgument("map-addr-levels=").nargs("?");
		
		try {
			Namespace namespace = parser.parseArgs(options.toArray(new String[options.size()]));
			
			initializeWriter(namespace.getString("out"));
			
			List<Object> tt = namespace.getList("translate_to");
			if(tt != null && tt.size() > 0) {
				this.translateTo = new ArrayList<Locale>();
				for(Object o : tt) {
					this.translateTo.add(Locale.forLanguageTag((String)o));
				}
			}
			
			String osmdocPath = namespace.getString("poi_catalog");
			if(osmdocPath.endsWith(".xml") || osmdocPath.equals("jar")) {
				reader = new DOCFileReader(osmdocPath);
			}
			else {
				reader = new DOCFolderReader(osmdocPath);
			}
			
			osmDocFacade = new OSMDocFacade(reader, null);
			
		} catch (ArgumentParserException e) {
			parser.handleError(e);
			System.exit(1);
		}
	}
	
	@Override
	protected void handlePoiPointAddrRow(JSONObject object, JSONObject address,
			String stripe) {
		
		// skip pois with empty addresses
		if(address == null) {
			return;
		}
		
		JSONObject result = new JSONObject();
		
		result.put("feature_id", object.getString("id"));
		result.put("uid", getUID(object, address));
		JSONObject tags = object.optJSONObject("properties");
		if(tags != null) {
			result.put("tags", tags);
		}
		
		if(osmDocFacade != null) {
			JSONArray poiTypes = object.getJSONArray("poiTypes");
			result.put("poi_types", poiTypes);
			
			List<Feature> poiTypeFeatures = new ArrayList<Feature>();
			for(int i = 0; i < poiTypes.length(); i++) {
				poiTypeFeatures.add(osmDocFacade.getFeature(poiTypes.getString(i)));
			}
			
			if(!translateTo.isEmpty()) {
				JSONObject poiTypesTranslated = new JSONObject();
				for(Locale l : translateTo) {
					for(Feature f : poiTypeFeatures) {
						String translatedTitle = osmDocFacade.getTranslatedTitle(f, l);
						poiTypesTranslated.put(l.toLanguageTag(), translatedTitle);
					}
				}
				result.put("poi_types_trans", poiTypesTranslated);
			}
			
			//TODO: Translate more tags for given laguages
			JSONObject moreTags = osmDocFacade.parseMoreTags(poiTypeFeatures, tags, tsc);
			result.put("more_tags", moreTags);
			
			List<String> keywords = new ArrayList<String>();
			osmDocFacade.collectKeywords(poiTypeFeatures, moreTags, keywords);
			
			result.put("keywords", keywords);
		}
		
		result.put("address_parts", address.optJSONArray("parts"));
		
		println(result.toString());
		
		flush();
	}
	

}
