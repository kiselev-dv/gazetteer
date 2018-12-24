package me.osm.osmdoc.read;

import static me.osm.osmdoc.localization.L10n.tr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import me.osm.osmdoc.localization.L10n;
import me.osm.osmdoc.model.Choise;
import me.osm.osmdoc.model.Feature;
import me.osm.osmdoc.model.Fref;
import me.osm.osmdoc.model.Group;
import me.osm.osmdoc.model.Hierarchy;
import me.osm.osmdoc.model.KeyType;
import me.osm.osmdoc.model.LangString;
import me.osm.osmdoc.model.MoreTags;
import me.osm.osmdoc.model.Tag;
import me.osm.osmdoc.model.Tags;
import me.osm.osmdoc.model.Trait;
import me.osm.osmdoc.model.Tag.TagValueType;
import me.osm.osmdoc.model.Tag.Val;
import me.osm.osmdoc.read.tagvalueparsers.OpeningHoursParser;
import me.osm.osmdoc.read.tagvalueparsers.TagValueParser;
import me.osm.osmdoc.read.tagvalueparsers.TagValueParsersFactory;
import me.osm.osmdoc.read.tagvalueparsers.TagsStatisticCollector;
import me.osm.osmdoc.read.util.TraitsParenFirstNavigator;
import me.osm.osmdoc.read.util.TraitsVisitor;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public class OSMDocFacade {
	
	private TagsDecisionTreeImpl dTree;
	private DOCReader docReader;
	private Set<Feature> excludedFeatures;
	private Map<String, Feature> featureByName = new HashMap<>();
	
	/*
	 * tagKey -> [tagVal, tagVal, tagVal] 
	 * 	  tagVal -> [featureType, featureType] 
	 * 
	 * eg:
	 * 
	 * amenity:[parking, place_of_worship]
	 * 
	 * place_of_worship: [place_of_worship, place_of_worship_christian, place_of_worship_jewish, ...]
	 * 
	 */
	private Map<String, Map<String, List<Feature>>> key2values = new HashMap<String, Map<String,List<Feature>>>();
	
	@SuppressWarnings("unchecked")
	public OSMDocFacade(String docPath) {
		this((docPath.endsWith(".xml") || docPath.equals("jar")) 
					? new DOCFileReader(docPath) 
					: new DOCFolderReader(docPath), Collections.EMPTY_LIST);
	}
	
	public OSMDocFacade(DOCReader reader, List<String> exclude) {
		
		docReader = reader;
		
		List<Feature> features = docReader.getFeatures();
		
		excludedFeatures = getBranches(exclude);
		
		for(Feature f : features) {
			
			if(excludedFeatures.contains(f)){
				continue;
			}
			
			featureByName.put(f.getName(), f);
			
			//synonyms
			for(Tags synonym : f.getTags()) {
				
				//our feature should match all of them
				List<Tag> tagsCombination = synonym.getTag();
				
				for(Tag t : tagsCombination) {
					//TODO: support key match
					String tagKey = t.getKey().getValue();
					if(!t.isExclude()) {
						if(!key2values.containsKey(tagKey)) {
							key2values.put(tagKey, new HashMap<String, List<Feature>>());
						}
						
						for(Tag.Val val : t.getVal()) {
							String tagVal = val.getValue();
							if(key2values.get(tagKey).get(tagVal) == null) {
								key2values.get(tagKey).put(tagVal, new ArrayList<Feature>());
							}
							
							key2values.get(tagKey).get(tagVal).add(f);
						}
					}
				}
			}
		}
		
		dTree = new TagsDecisionTreeImpl(key2values);
	}

	public Set<Feature> getBranches(List<String> exclude) {
		String hierarcyName = null;
		boolean singleHierarcy = docReader.listHierarchies().size() == 1;
		if(singleHierarcy) {
			hierarcyName = docReader.listHierarchies().get(0).getName();
		}
		
		return getBranches(exclude, hierarcyName, singleHierarcy);
	}
	
	private Set<Feature> getBranches(List<String> exclude, String hierarcyName,
			boolean singleHierarcy) {
		Set<Feature> result = new HashSet<Feature>();
		if(exclude != null) {
			for(String ex : exclude) {
				String[] split = StringUtils.split(ex, ':');
				if(singleHierarcy && split.length == 1) {
					result.addAll(docReader.getHierarcyBranch(hierarcyName, ex));
				}
				else {
					result.addAll(docReader.getHierarcyBranch(split[0], split[1]));
				}
			}
		}
		return result;
	}
	
	public JSONObject getHierarchyJSON(String hierarchy, Locale lang) {

		Hierarchy h = docReader.getHierarchy(hierarchy);
		
		if(h != null) {
			JSONObject result = new JSONObject();
			List<JSONObject> groups = new ArrayList<JSONObject>();
			for(Group g : h.getGroup()) {
				JSONObject gjs = new JSONObject();
				gjs.put("name", g.getName());
				gjs.put("icon", g.getIcon());
				gjs.put("title", tr(g.getTitle(), lang));
				groups.add(gjs);
				
				dfsGroup(gjs, g.getGroup(), g.getFref(), lang);
			}
			
			result.put("groups", new JSONArray(groups));
			return result;
		}
		
		return null;
		
	}

	private void dfsGroup(JSONObject gjs, List<Group> groups, List<Fref> fref, Locale lang) {
		
		List<JSONObject> childGroups = new ArrayList<JSONObject>();
		
		for(Group g : groups) {
			JSONObject childG = new JSONObject();
			childG.put("name", g.getName());
			childG.put("icon", g.getIcon());
			childG.put("title", tr(g.getTitle(), lang));
			childGroups.add(childG);

			dfsGroup(childG, g.getGroup(), g.getFref(), lang);
		}
		
		gjs.put("groups", new JSONArray(childGroups));
		
		List<JSONObject> childFeatures = new ArrayList<JSONObject>();
		for(Fref f : fref) {
			Feature feature = getFeature(f.getRef());
			if(feature != null) {
				JSONObject childFeature = featureAsJSON(feature, lang);

				childFeature.put("icon", feature.getIcon());
				childFeature.put("description", tr(feature.getDescription(), lang));
				
				childFeatures.add(childFeature);
			}
		}
		
		gjs.put("features", new JSONArray(childFeatures));
		
	}

	public TagsDecisionTreeImpl getPoiClassificator() {
		return dTree;
	}

	public Feature getFeature(String poiClass) {
		return featureByName.get(poiClass);
	}

	public Collection<Feature> getFeature(Collection<String> poiClass) {
		List<Feature> result = new ArrayList<>(poiClass.size());
		for(String s : poiClass) {
			result.add(featureByName.get(s));
		}
		return result;
	}

	public String getTranslatedTitle(Feature fClass, Locale lang) {
		return L10n.tr(fClass.getTitle(), lang);
	}

	public String getTranslatedTitle(Feature fClass, Tag td, Locale lang) {
		return L10n.tr(td.getTitle(), lang);
	}

	public String getTranslatedTitle(Feature fClass, Val valuePattern,
			Locale lang) {
		return L10n.tr(valuePattern.getTitle(), lang);
	}

	public List<String> listPoiClassNames(Collection<Feature> poiClassess) {
		
		LinkedHashSet<String> result = new LinkedHashSet<String>();
		for(Feature f : poiClassess) {
			for(String lang : L10n.supported) {
				//Translated title
				result.add(getTranslatedTitle(f, Locale.forLanguageTag(lang)));
			}
		}
		
		return new ArrayList<String>(result);
		
	}

	public LinkedHashMap<String, Tag> collectMoreTags(Collection<Feature> poiClassess, 
			LinkedHashSet<String> visitedTraits) {
		
		List collected = collectTagsWithTraits(poiClassess, visitedTraits, false);
		
		LinkedHashMap<String, Tag> result = tagsAndTraitsAsTagsMap(collected);
		
		return result;
	}

	private LinkedHashMap<String, Tag> tagsAndTraitsAsTagsMap(List collected) {
		LinkedHashMap<String, Tag> result = new LinkedHashMap();
		for(Object o : collected) {
			if (o instanceof Trait) {
				MoreTags moreTags = ((Trait) o).getMoreTags();
				if(moreTags != null && moreTags.getTag() != null) {
					for(Tag t : moreTags.getTag()) {
						result.put(t.getKey().getValue(), t);
					}
				}
			}
			else if (o instanceof MoreTags) {
				List<Tag> tags = ((MoreTags) o).getTag();
				if(tags != null) {
					for(Tag t : tags) {
						result.put(t.getKey().getValue(), t);
					}
				}
			}
			else if (o instanceof Tag) {
				result.put(((Tag)o).getKey().getValue(), (Tag)o);
			}
		}
		return result;
	}

	public LinkedHashMap<String, Tag> collectMoreTags(Collection<Feature> poiClassess) {
		return collectMoreTags(poiClassess, new LinkedHashSet<String>());
	}
	
	public JSONObject parseMoreTags(List<Feature> poiClassess, JSONObject properties, 
			TagsStatisticCollector statistics, Map<String, List<Val>> fillVals) {
		
		LinkedHashMap<String, Tag> moreTags = collectMoreTags(poiClassess);
		JSONObject result = new JSONObject();
		
		for(Entry<String, Tag> template : moreTags.entrySet()) {

			String key = template.getKey();
			Tag tag = template.getValue();
			
			Object o = properties.opt(key);
			String rawValue = (o != null ? o.toString() : null);

			//value founded
			if(StringUtils.isNotBlank(rawValue)) {
				TagValueParser parser = getTagValueParser(tag);
				
				try{
					Object parsedValue = null;
					
					// Symbol ';' already used in working_hours to split
					// different time periods
					if(parser instanceof OpeningHoursParser || rawValue.indexOf(';') < 0) {
						
						parsedValue = parser.parse(rawValue);
						
						
						if(parsedValue != null) {
							Val val = null;
							
							if(parsedValue instanceof Val) {
								val = (Val) parsedValue;
								parsedValue = ((Val) parsedValue).getValue();
							}

							statistics.success(parsedValue, tag, val, rawValue, parser, poiClassess);
							
							fillVals(fillVals, tag, parsedValue);
						}
						
					}
					//Multiple values
					else {
						parsedValue = new JSONArray();
						
						//For now we use ; as values separator
						for(String v : StringUtils.split(rawValue, ';')) {
							Object pv = parser.parse(v);
							if(pv != null) {
								
								Val val = null;
								
								if(pv instanceof Val) {
									val = (Val) pv;
									pv = ((Val) pv).getValue();
								}
								
								((JSONArray)parsedValue).put(pv);
								
								statistics.success(pv, tag, val, rawValue, parser, poiClassess);
								
								fillVals(fillVals, tag, pv);
							}
							else {
								statistics.failed(tag, rawValue, parser, poiClassess);
							}
						}
						
						if(((JSONArray)parsedValue).length() == 0) {
							parsedValue = null;
						}
					}
					
					if(parsedValue != null) {
						result.put(key, parsedValue);
					}
					else {
						statistics.failed(tag, rawValue, parser, poiClassess);
					}
				}
				catch (Throwable t) {
					statistics.failed(tag, rawValue, parser, poiClassess);
				}
			}
		}
		
		return result;
	}

	private void fillVals(Map<String, List<Val>> fillVals, Tag tag,
			Object parsedValue) {
		
		if(parsedValue instanceof Val && fillVals != null) {
			
			String keyKey = tag.getKey().getValue();
			
			if(fillVals.get(keyKey) == null) {
				fillVals.put(keyKey, new ArrayList<Tag.Val>());
			}
			
			fillVals.get(keyKey).add((Val) parsedValue);
		}
	}

	private TagValueParser getTagValueParser(Tag tag) {
		return TagValueParsersFactory.getParser(tag);
	}
	
	public List<?> collectTagsWithTraits(Collection<Feature> features, 
			LinkedHashSet<String> visitedTraits, boolean onlyCommon ) {
		
		TraitsParenFirstNavigator nav = new TraitsParenFirstNavigator(this);
		Map<String, Integer> traitsCount = new LinkedHashMap<String, Integer>();
		
		HashMap<String, Tag> tags = new HashMap<>();
		LinkedHashMap<String, Integer> tagsCounts = new LinkedHashMap<>();
		
		for(Feature feature : features) {
			
			LinkedHashSet<String> visited = new LinkedHashSet<String>();
			nav.visit(feature, TraitsVisitor.VOID_VISITOR, visited);
			visitedTraits.addAll(visited);
			for(String s : visited) {
				if(traitsCount.get(s) == null) {
					traitsCount.put(s, 0);
				}
				traitsCount.put(s, traitsCount.get(s) + 1);
			}
			
			MoreTags moreTags = feature.getMoreTags();
			if(moreTags != null && moreTags.getTag() != null) {
				for(Tag tg : moreTags.getTag()) {
					String tagKey = tg.getKey().getValue();
					if(tagsCounts.get(tagKey) == null) {
						tagsCounts.put(tagKey, 0);
					}
					tagsCounts.put(tagKey, tagsCounts.get(tagKey) + 1);
					tags.put(tagKey, tg);
				}
			}
		}
		
		List<Object> result = new ArrayList<>();
		
		for(Map.Entry<String, Integer> traitsEntry : traitsCount.entrySet()) {
			// Every feature has that trait
			if(onlyCommon && traitsEntry.getValue() != features.size()) {
				continue;
			}
			
			Trait trait = getTraitByName(traitsEntry.getKey());
			if(trait.isGroupTags()) {
				result.add(trait);
			}
			else {
				result.add(trait.getMoreTags());
			}
		}
		
		
		for(Map.Entry<String, Integer> tagEntry : tagsCounts.entrySet()) {
			if(onlyCommon && tagEntry.getValue() != features.size()) {
				continue;
			}
			
			String tagKey = tagEntry.getKey();
			Tag tag = tags.get(tagKey);
			
			result.add(tag);
		}
		
		return result;
	}
	
	public Trait getTraitByName(String trait) {
		return docReader.getTraits().get(trait);
	} 

	public Trait getTraitByRef(Feature.Trait trait) {
		if(trait != null) {
			return docReader.getTraits().get(trait.getValue());
		}
		return null;
	} 

	public JSONObject featureAsJSON(Feature f, Locale lang) {
		
		JSONObject result = new JSONObject();
		
		result.put("name", f.getName());
		result.put("title", f.getTitle());
		
		if(lang == null) {
			JSONArray titles = new JSONArray();
			JSONObject lang2title = new JSONObject();
			
			for(String l : L10n.supported) {
				String translatedTitle = getTranslatedTitle(f, Locale.forLanguageTag(l));
				titles.put(translatedTitle);
				lang2title.put(l, translatedTitle);
			}
			
			result.put("translated_title", titles);
			result.put("title_by_lang", lang2title);
		}
		else {
			result.put("translated_title", getTranslatedTitle(f, lang));
		}
		
		JSONArray keywords = new JSONArray();
		for(LangString ls : f.getKeyword()) {
			String keywordLang = ls.getLang();
			String keyword = StringUtils.strip(ls.getValue()); 
			
			JSONObject keywordJS = new JSONObject();
			keywordJS.put("alias", keyword);
			keywordJS.put("lang", keywordLang);
			
			keywords.put(keywordJS);
		}
		result.put("keywords", keywords);
		
		LinkedHashSet<String> traits = new LinkedHashSet<String>();
		LinkedHashMap<String, Tag> moreTags = collectMoreTags(Arrays.asList(f), traits);

		result.put("traits", new JSONArray(traits));
		
		JSONObject moreTagsJS = new JSONObject();
		for(Entry<String, Tag> tagE : moreTags.entrySet()) {
			moreTagsJS.put(tagE.getKey(), tagValuesAsJSON(tagE.getValue(), lang));
		}
		
		result.put("more_tags", moreTagsJS);
			
		return result;
	}

	private JSONObject tagValuesAsJSON(Tag tag, Locale lang) {
		JSONObject tagJS = new JSONObject();
		
		if(lang == null) {
			tagJS.put("name", tag.getTitle());
		}
		else {
			tagJS.put("name", L10n.tr(tag.getTitle(), lang));
		}
		
		JSONObject valuesJS = new JSONObject();
		tagJS.put("valueType", tag.getTagValueType());
		
		if(tag.getTagValueType() != TagValueType.BOOLEAN) {
			for(Val val : tag.getVal()) {
				JSONObject value = new JSONObject();
				String valTrKey = StringUtils.strip(val.getTitle());
				if(lang == null) {
					value.put("name", valTrKey);
				}
				else {
					value.put("name", L10n.tr(valTrKey, lang));
				}
				value.put("group", val.isGroupByValue());
				
				valuesJS.put(val.getValue(), value);
			}
		}
		
		tagJS.put("values", valuesJS);
		
		return tagJS;
	}
	
	public List<JSONObject> listTranslatedFeatures(Locale lang) {
		
		List<JSONObject> reult = new ArrayList<JSONObject>();
		
		for(Feature f : this.docReader.getFeatures()) {
			reult.add(featureAsJSON(f, lang));
		}
		
		return reult;
	}

	public void collectKeywords(Collection<Feature> poiClassess, 
			Map<String, List<Val>> moreTagsVals,
			Collection<String> keywords, Collection<String> langs) {
		
		for(Feature f : poiClassess) {
			for(LangString kw : f.getKeyword()) {
				addKeyword(keywords, kw, langs);
			}
		}
		
		if(moreTagsVals != null) {
			for(List<Val> vl : moreTagsVals.values()) {
				for(Val v : vl) {
					for(LangString kw : v.getKeyword()) {
						addKeyword(keywords, kw, langs);
					}
				}
			}
		}
	}

	private void addKeyword(Collection<String> keywords, LangString kw, 
			Collection<String> langs) {
		
		String val = kw.getValue();
		
		if(val.startsWith(L10n.L10N_PREFIX)) {
		
			if(langs != null && !langs.isEmpty()) {
				for(String l : langs) {
					if(L10n.supported.contains(l)) {
						keywords.add(L10n.tr(val, Locale.forLanguageTag(l)));
					}
				}
			}
			else {
				for(String l : L10n.supported) {
					keywords.add(L10n.tr(val, Locale.forLanguageTag(l)));
				}
			}
		}
		else if(langs == null || langs.isEmpty()) {
			keywords.add(val);
		}
		else {
			String lang = kw.getLang();
			if(langs.contains(lang)) {
				keywords.add(val);
			}
		}
	}
	
	public Map<String, String> listMoreTagsTypes() {
		Map<String, String> result = new HashMap<String, String>();

		LinkedHashMap<String, Tag> moreTags = collectMoreTags(featureByName.values());
		
		for(Entry<String, Tag> e : moreTags.entrySet()) {
			result.put(e.getKey(), e.getValue().getTagValueType().name());
		}
		
		return result;
	}
	
	public DOCReader getReader() {
		return docReader;
	}

	public JSONObject collectCommonTagsWithTraitsJSON(Collection<Feature> features, Locale lang) {
		List<?> collected = collectTagsWithTraits(features, new LinkedHashSet<String>(), true);
		
		JSONArray tagOptions = new JSONArray();
		LinkedHashMap<String, Tag> groupedTags = new LinkedHashMap();
		for(Object o : collected) {
			if (o instanceof Trait) {
				JSONObject tagsAsVals = new JSONObject();
				tagOptions.put(tagsAsVals);
				
				JSONArray options = new JSONArray();
				tagsAsVals.put("key", "trait_" + ((Trait) o).getName());
				tagsAsVals.put("title", L10n.tr(((Trait) o).getTitle(), lang));
				tagsAsVals.put("type", "GROUP_TRAIT");
				tagsAsVals.put("options", options);
				
				MoreTags moreTags = ((Trait) o).getMoreTags();
				if(moreTags != null && moreTags.getTag() != null) {
					for(Tag t : moreTags.getTag()) {
						groupedTags.put(t.getKey().getValue(), t);
						
						JSONObject option = new JSONObject();
						options.put(option);
						
						option.put("valueKey", t.getKey().getValue());
						option.put("valueTitle", L10n.tr(t.getTitle(), lang));
					}
				}
			}
			else if (o instanceof MoreTags) {
				List<Tag> tags = ((MoreTags) o).getTag();
				if(tags != null) {
					for(Tag t : tags) {
						JSONObject tag = tagAsJSONOption(t, lang);
						tagOptions.put(tag);
					}
				}
			}
			else if (o instanceof Tag) {
				JSONObject tag = tagAsJSONOption((Tag) o, lang);
				tagOptions.put(tag);
			}
		}
		
		JSONObject grouped = new JSONObject();
		for(Tag t : groupedTags.values()) {
			grouped.put(t.getKey().getValue(), tagAsJSONOption(t, lang));
		}
		
		JSONObject result = new JSONObject();
		result.put("groupedTags", grouped);
		result.put("commonTagOptions", tagOptions);
		
		return result;
	}

	private JSONObject tagAsJSONOption(Tag t, Locale lang) {
		JSONObject result = new JSONObject();
		result.put("key", t.getKey().getValue());
		result.put("title", L10n.tr(t.getTitle(), lang));
		result.put("type", t.getTagValueType().toString());
		
		if(t.getTagValueType() == TagValueType.ENUM) {
			JSONArray options = new JSONArray();
			result.put("options", options);
			
			for(Val v : t.getVal()) {
				JSONObject option = new JSONObject();
				options.put(option);
				
				option.put("valueKey", v.getValue());
				option.put("valueTitle", L10n.tr(v.getTitle(), lang));
			}
		}
		
		return result;
	}

	
}
