package me.osm.osmdoc.imports.osmcatalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import me.osm.osmdoc.imports.osmcatalog.model.CatalogItem;
import me.osm.osmdoc.imports.osmcatalog.model.Tag;
import me.osm.osmdoc.imports.osmcatalog.model.TagDescriptor;

import org.json.JSONArray;
import org.json.JSONObject;

public class JSONCatalogParser {
	
	public static final String ROOT_ELEMENT_NAME = "ROOT";
	private LinkedHashMap<String, JSONObject> ciName2JSON = new LinkedHashMap<>();
	
	private LinkedHashMap<String, CatalogItem> catalog = new LinkedHashMap<>();
	private LinkedHashMap<String, CatalogItem> excluded = new LinkedHashMap<>();
	
	private JSONArray catalogJSON;
	private Set<String> excludeCatalogItems;
	
	public JSONCatalogParser(JSONArray catalogJSON, JSONArray interfaces, Set<String> excludeCatalogItems, Set<String> excludeInterfaces) {
		this.catalogJSON = catalogJSON;
		this.excludeCatalogItems = excludeCatalogItems;
	}
	
	public JSONCatalogParser(JSONArray catalogJSON, Set<String> excludeCatalogItems, Set<String> excludeInterfaces) {
		this.catalogJSON = catalogJSON;
		this.excludeCatalogItems = excludeCatalogItems;
	}

	public LinkedHashMap<String, CatalogItem> parse() {
		
		//parseInterfaces();
		
		for(int i = 0; i < catalogJSON.length(); i++) {
			JSONObject catalogEntry = catalogJSON.getJSONObject(i);
			ciName2JSON.put(catalogEntry.getString("name"), catalogEntry);
		}
		
		catalog.put(ROOT_ELEMENT_NAME, new CatalogItem().setName(ROOT_ELEMENT_NAME));
		for(Entry<String, JSONObject> entry : ciName2JSON.entrySet()) {
			parseItem(entry.getValue());
		}
		
		ciName2JSON.clear();
		
		List<CatalogItem> sortedCatalogItems = new ArrayList<>(catalog.values());
		
		//для начала сортируем по количеству тегов, потом среди тех у, 
		//кого тегов больше чем 1 
		Collections.sort(sortedCatalogItems, new Comparator<CatalogItem>() {
			@Override
			public int compare(CatalogItem o1, CatalogItem o2) {
				return o2.getTags().size() - o1.getTags().size();
			}
		});

		Map<String, List<Tag>> tags2Exclude = new HashMap<>();
		for(CatalogItem ci : sortedCatalogItems) {
			
			if(ci.getTags().size() > 1) {
				
				for(Tag tag : ci.getTags()) {
					if(tags2Exclude.get(tag.toString()) == null) {
						tags2Exclude.put(tag.toString(), new ArrayList<Tag>());
					}
					tags2Exclude.get(tag.toString()).addAll(ci.getTags());
				}
			}
			
			//пока только 1 уточняющий тэг, если станет больше надо для тех у кого 
			//2 тэга (1 уточняющий) проверять не повторяются ли теги среди тех у кого 
			//3 тега (2 уточняющих) 
			addExcludeTags(ci, tags2Exclude);
		}

		for(CatalogItem ci : sortedCatalogItems) {
			for(Tag t : ci.getTags()) {
				Iterator<Tag> i = ci.getExcludeTags().iterator();
				while(i.hasNext()) {
					Tag ti = i.next();
					if(t.getKey().equals(ti.getKey())){
						i.remove();
					}
				}
			}
		}

		for(CatalogItem ci : sortedCatalogItems) {
			if(skipItem(ci, excludeCatalogItems)) {
				excluded.put(ci.getName(), ci);
			}
		}

		for(String ex : excluded.keySet()) {
			catalog.remove(ex);
		}

		return catalog;
	}
	
	/**
	 * Для каждого тега объекта исключаем все уточняющие его теги
	 * */
	private static void addExcludeTags(CatalogItem ci, Map<String, List<Tag>> tags2Exclude) {
		for(Tag tag : ci.getTags()){
			if(tags2Exclude.get(tag.toString()) != null) {
				ci.getExcludeTags().addAll(tags2Exclude.get(tag.toString()));
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void parseItem(JSONObject catalogEntry) {
		
		CatalogItem item = new CatalogItem();

		String name = catalogEntry.getString("name");
		item.setName(name);

		JSONObject tags = catalogEntry.getJSONObject("tags");
		Iterator<String> tagKeys = tags.keys();
		while (tagKeys.hasNext()) {
			String tagKey = tagKeys.next();
			item.getTags().add(new Tag(tagKey, tags.getString(tagKey)));
		}
		
		JSONObject mt = catalogEntry.getJSONObject("moretags");
		item.setMoreTags(parseMoreTags(mt));
		
		JSONArray applyedFor = catalogEntry.getJSONArray("type");
		List<String> type = new ArrayList<>();
		for(int i = 0; i < applyedFor.length(); i++) {
			type.add(applyedFor.getString(i));
		}
		item.setType(type);
		
		item.setPoi(catalogEntry.optBoolean("poi", false));
		
		//Добавляем в каталог тут, подстраховываясь от цикла в инициализации родителей
		//Вдруг ктонибудь добавит самого себя в родителеи
		catalog.put(name, item);

		JSONArray parents = catalogEntry.getJSONArray("parent");
		
		if(parents.length() == 0) {
			item.getParent().add(catalog.get(ROOT_ELEMENT_NAME));
		}
		
		for(int i = 0; i < parents.length(); i++) {
			String parentName = parents.getString(i);
			if(catalog.get(parentName) == null) {
				parseItem(ciName2JSON.get(name));
			}
			item.getParent().add(catalog.get(parentName));
		}
		
	}

	/**
	 * Parse moreTags
	 * */
	private List<TagDescriptor> parseMoreTags(JSONObject mt) {
		
		List<TagDescriptor> moretags = new ArrayList<>();
		
		@SuppressWarnings("unchecked")
		Iterator<String> mti = mt.keys();
		while (mti.hasNext()) {
			String tagKey = mti.next();
			JSONObject additionalTag = mt.getJSONObject(tagKey);
			
			TagDescriptor td = new TagDescriptor();
			td.setId(tagKey);
			
			String osmTag = additionalTag.getString("tag");
			td.setOsmTagName(osmTag);
			
			String type = additionalTag.getString("type");
			td.setType(type);
			
			if("translate".equals(type)) {
				String clazz = additionalTag.getString("class");
				td.setClazz(clazz);
			}
			
			boolean multi = "yes".equals(additionalTag.optString("multivalue")) ? true : additionalTag.optBoolean("multivalue");
			td.setMultyValue(multi);
			
			moretags.add(td);
		}
		return moretags;
	}

	public LinkedHashMap<String, CatalogItem> getCatalog() {
		return catalog;
	}

	public LinkedHashMap<String, CatalogItem> getExcluded() {
		return excluded;
	}
	
	private static boolean skipItem(CatalogItem ci, Set<String> exclude) {
		
		if("".equals(ci.getName())) {
			return false;
		}
		
		if(exclude.contains(ci.getName())) {
			return true;
		}
		
		for(CatalogItem p : ci.getParent()) {
			if(skipItem(p, exclude)) {
				exclude.add(ci.getName());
				return true;
			}
		}
		return false;
	}
	
	public Map<String, List<String>> getParent2Childs() {
		Map<String, List<String>> result = new HashMap<String, List<String>>();
		
		for(CatalogItem ci : catalog.values()) {
			for(CatalogItem parent : ci.getParent()) {
				if(result.get(parent.getName()) == null) {
					result.put(parent.getName(), new ArrayList<String>());
				}
				result.get(parent.getName()).add(ci.getName());
			}
		}
		
		return result;
	}
	
	public static Set<String> getExclude(Properties properties) {
		Set<String> exclude = new HashSet<>();
		String excludeString = properties.getProperty("catalog.exclude");
		if(null != excludeString && !"".equals(excludeString)) {
			for(String ex : excludeString.split(",")) {
				String t = ex.trim();
				if(!"".equals(t))
					exclude.add(t);
			}
		}
		return exclude;
	}
}
