package me.osm.gazetter.out;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.osmdoc.model.Feature;
import me.osm.osmdoc.model.MoreTags;
import me.osm.osmdoc.model.Tag;
import me.osm.osmdoc.model.Tag.TagValueType;
import me.osm.osmdoc.model.Tag.Val;
import me.osm.osmdoc.read.OSMDocFacade;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

public class PoiValueExctractorImpl extends FeatureValueExctractorImpl {
	
	private static final String POI_CLASS = "poi-class";
	private static final String MORE_TAGS = "more-tags";
	private OSMDocFacade osmDocFacade;

	public PoiValueExctractorImpl(OSMDocFacade osmDocFacade) {
		this.osmDocFacade = osmDocFacade;
	}

	@Override
	public Object getValue(String key, JSONObject jsonObject, Integer rowIndex) {
		try {
			
			String lang = null;
			String format = "json";
			
			if(key.contains(".json")) {
				format = "json";
				key = StringUtils.remove(key, ".json");
			}
			else if(key.contains(".hstore")) {
				format = "hstore";
				key = StringUtils.remove(key, ".hstore");
			}
			
			String[] split = StringUtils.split(key, ':');
			if(split.length == 2) {
				key = split[0];
				lang = split[1];
			}
			
			String poiClass = jsonObject.getJSONArray("poiTypes").getString(0);
			Feature fClass = osmDocFacade.getFeature(poiClass);

			JSONObject properties = jsonObject.getJSONObject(GeoJsonWriter.PROPERTIES);
			switch (key) {
			
			//class, class_ru
			case POI_CLASS:
				if(lang == null) {
					return poiClass;
				}
				else {
					return osmDocFacade.getTranslatedTitle(fClass, lang);
				}
				
			//tags, tags_ru
			case MORE_TAGS:
				
				Map<String, String> result = new HashMap<>();
				
				MoreTags moreTags = fClass.getMoreTags();
				for(Tag td : moreTags.getTag()) {
					
					String tagKey = td.getKey().getValue();
					
					String tagKeyName = tagKey;
					if(lang != null) {
						tagKeyName = osmDocFacade.getTranslatedTitle(fClass, td, lang);
					}
					
					String valueString = Objects.toString(properties.opt(tagKey), "");
					String valueName = valueString;
					if(td.getTagValueType() == TagValueType.ENUM && lang != null) {
						
						//drop tag if there is no translated value parsed. 
						valueName = "";

						//write out untranslated value 
						//valueName = valueString;

						//write out constant 
						//valueName = "translation-missed";
						
						for(Val valuePattern : td.getVal()) {
							if(valuePattern.getValue().equals(valueString)) {
								valueName = osmDocFacade.getTranslatedTitle(fClass, valuePattern, lang);
							}
						}
						
					}

					if(StringUtils.isNoneBlank(valueName)) {
						result.put(tagKeyName, valueName);
					}
					
				}
				
				if("json".equals(format)) {
					return new JSONObject(result).toString();
				}
				else {
					return FeatureValueExctractorImpl.asHStore(result);
				}
				
			//operator, opening_hours, brand, phone, fax, website, email
			case "operator":
				return properties.optString("operator");

			case "opening_hours":
				return properties.optString("opening_hours");

			case "brand":
				return properties.optString("brand");

			case "phone":
				String val = properties.optString("contact:phone");
				if(StringUtils.isBlank(val)) {
					return properties.optString("phone");
				}
				return val;
			
			case "fax":
				val = properties.optString("contact:fax");
				if(StringUtils.isBlank(val)) {
					return properties.optString("fax");
				}
				return val;

			case "website":
				val = properties.optString("contact:website");
				if(StringUtils.isBlank(val)) {
					return properties.optString("website");
				}
				return val;
			
			case "email":
				val = properties.optString("contact:email");
				if(StringUtils.isBlank(val)) {
					return properties.optString("email");
				}
				return val;

			default:
				return super.getValue(key, jsonObject, rowIndex);
			}
		}
		catch (Exception e) {
			return null;
		}
		
	}

	@Override
	public Collection<String> getSupportedKeys() {
		List<String> suuported = Arrays.asList(POI_CLASS);
		suuported.addAll(super.getSupportedKeys());
		return suuported;
	}
	
	private Set<String> contacts = new HashSet<String>(
			Arrays.asList("operator", "brand", "opening_hours",
					"phone", "fax", "website", "email"));
	
	@Override
	public boolean supports(String key) {
		return super.supports(key) 
				|| key.startsWith(POI_CLASS)
				|| key.startsWith(MORE_TAGS)
				|| contacts.contains(key);
	}

}
