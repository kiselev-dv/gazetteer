package me.osm.osmdoc.imports.osmcatalog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import me.osm.osmdoc.imports.osmcatalog.model.CatalogItem;
import me.osm.osmdoc.imports.osmcatalog.model.TagDescriptor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class JSONTreeGenerator {
	
	public static void main(String[] args) {
		
		try {
			
			if(args.length == 0){
				System.out.println("Usage: SQLGenerator prorties.file");
				System.exit(1);
			}
			
			if(!new File(args[0]).exists()) {
				System.out.println("Cant open properties file");
				System.exit(1);
			}
			
			Properties properties = new Properties();
			properties.load(new FileInputStream(new File(args[0])));

			Set<String> exclude = JSONCatalogParser.getExclude(properties);
			
			JSONArray catalogJSON = new JSONArray(new JSONTokener(new FileInputStream(properties.getProperty("catalog.file"))));
			//JSONArray interfaces = new JSONArray(new JSONTokener(new FileInputStream(properties.getProperty("interfaces.file"))));
			JSONArray interfaces = null;
			
			JSONCatalogParser jsonCatalogParser = new JSONCatalogParser(catalogJSON, interfaces, exclude, new HashSet<String>());
			LinkedHashMap<String, CatalogItem> catalog = jsonCatalogParser.parse();
			
			Map<String, List<String>> parent2Childs = jsonCatalogParser.getParent2Childs();
			
			JSONObject dictionary = new JSONObject(Utils.getFileContent(new File(properties.getProperty("dictionary.file"))));
			
			JSONArray objects = new JSONArray();
			if(parent2Childs.get(JSONCatalogParser.ROOT_ELEMENT_NAME) != null) {
				for(String child:parent2Childs.get(JSONCatalogParser.ROOT_ELEMENT_NAME)) {
					objects.put(go(child, parent2Childs, catalog, dictionary));
				}
			}
			
			System.out.println(objects.toString(4));
			//System.out.println(objects.toString());
			
		} catch (JSONException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static JSONObject go(String parent, Map<String, List<String>> parent2Childs,
			LinkedHashMap<String, CatalogItem> catalog, JSONObject dictionary) {

		JSONObject result = new JSONObject();
		
		String title = getTitle(parent, dictionary);
		
		result.put("title", title);
		result.put("name", parent);
		
		JSONArray moreTags = new JSONArray();
		for(TagDescriptor td : catalog.get(parent).getMoreTags()) {
			moreTags.put(getTagTitle(td.getId(), dictionary));
		}
		result.put("moretags", moreTags);
		
		JSONArray childs = new JSONArray();
		if(parent2Childs.get(parent) != null) {
			for(String child:parent2Childs.get(parent)) {
				childs.put(go(child, parent2Childs, catalog, dictionary));
			}
		}
		result.put("childs", childs);
		
		return result;
		
	}

	public static String getTagTitle(String id, JSONObject dictionary) {
		try {
			JSONObject jsonObject = dictionary.getJSONObject("moretags").getJSONObject(id);
			
			if(jsonObject != null) {
				return jsonObject.getString("name");
			}
		}
		catch (JSONException e) {
			
		}
		return null;
	}

	public static String getTitle(String name, JSONObject dictionary) {
		
		try {
			JSONObject jsonObject = dictionary.getJSONObject("catalog").getJSONObject(name);
			
			if(jsonObject != null) {
				return jsonObject.getString("name");
			}
		}
		catch (JSONException e) {
			
		}
		return null;
	}

	public static String getDescription(String name, JSONObject dictionary) {
		
		try {
			JSONObject jsonObject = dictionary.getJSONObject("catalog").getJSONObject(name);
			
			if(jsonObject != null) {
				return jsonObject.getString("description");
			}
		}
		catch (JSONException e) {
			
		}
		return null;
	}

	public static String getLink(String name, JSONObject dictionary) {
		
		try {
			JSONObject jsonObject = dictionary.getJSONObject("catalog").getJSONObject(name);
			
			if(jsonObject != null) {
				return jsonObject.getString("description");
			}
		}
		catch (JSONException e) {
			
		}
		return null;
	}

	public static JSONObject getTagValues(String clazz,
			JSONObject dictionary) {
		
		return dictionary.getJSONObject("class").optJSONObject(clazz);
		
	}

}
