package me.osm.osmdoc.imports.osmcatalog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import me.osm.osmdoc.imports.osmcatalog.model.CatalogItem;
import me.osm.osmdoc.imports.osmcatalog.model.Tag;
import me.osm.osmdoc.imports.osmcatalog.model.TagDescriptor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class JOSMPresetsGenegartor {
	
	public static void main(String[] args) {
		
		try {
			
			if(args.length < 2){
				System.out.println("Usage: JOSMPresetsGenegartor prorties.file output.file");
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
			JSONArray interfaces = new JSONArray(new JSONTokener(new FileInputStream(properties.getProperty("interfaces.file"))));
			
			JSONCatalogParser jsonCatalogParser = new JSONCatalogParser(catalogJSON, interfaces, exclude, new HashSet<String>());
			LinkedHashMap<String, CatalogItem> catalog = jsonCatalogParser.parse();
			
			Map<String, List<String>> parent2Childs = jsonCatalogParser.getParent2Childs();
			
			JSONObject dictionary = new JSONObject(Utils.getFileContent(new File(properties.getProperty("dictionary.file"))));
			
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
	 
			// root elements
			Document doc = docBuilder.newDocument();
			Element presets = doc.createElementNS("http://josm.openstreetmap.de/tagging-preset-1.0", "presets");
			doc.appendChild(presets);
			
			Element topGroup = doc.createElement("group");
			topGroup.setAttribute("name", "OSM Catalog presets");
			
			for(String itemName : parent2Childs.get(JSONCatalogParser.ROOT_ELEMENT_NAME)) {
				for(Node n : getPreset(catalog.get(itemName), catalog, parent2Childs, dictionary, doc)) {
					topGroup.appendChild(n);
				}
			}
			
			presets.appendChild(topGroup);
			
			writeOut(doc, new File(args[1]));
			
		} catch (JSONException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static void writeOut(Document doc, File file) {
		try	 {
			TransformerFactory tf = TransformerFactory.newInstance();
			Transformer transformer = tf.newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
			transformer.setOutputProperty(OutputKeys.METHOD, "xml");
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
			
			transformer.transform(new DOMSource(doc), 
					new StreamResult(new OutputStreamWriter(new FileOutputStream(file), "UTF-8")));
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static List<Element> getPreset(CatalogItem item, LinkedHashMap<String,CatalogItem> catalog, Map<String, List<String>> parent2Childs, JSONObject dictionary, Document doc) {

		List<Element> result = new ArrayList<>();
		Element itemNode = null;
		Element childGroupNode = null;
		
		String langPrefix = dictionary.getString("language");
		
		if(item.getTags().isEmpty()) {
			childGroupNode = doc.createElement("group");
			childGroupNode.setAttribute("name", JSONTreeGenerator.getTitle(item.getName(), dictionary));
		}
		else {
			
			itemNode = doc.createElement("item");
			itemNode.setAttribute("name", item.getName());
			itemNode.setAttribute(langPrefix + "." + "name", JSONTreeGenerator.getTitle(item.getName(), dictionary));
			
			itemNode.setAttribute("icon", "http://amdmi3.ru/files/osmCatalog/poi_marker/" + item.getName() + ".png");
			
			for(Tag tagValue : item.getTags()) {
				Element key = doc.createElement("key");
				key.setAttribute("key", tagValue.getKey());
				key.setAttribute("value", tagValue.getValue());
				itemNode.appendChild(key);
			}
			
			for(TagDescriptor tg : item.getMoreTags()) {
				String type = tg.getType();
				String clazz = tg.getClazz();
				
				if(type.equals("string") || type.equals("namelang")) {
					Element text = doc.createElement("text");
					text.setAttribute("key", tg.getOsmTagName());
					text.setAttribute("text", JSONTreeGenerator.getTagTitle(tg.getId(), dictionary));
					itemNode.appendChild(text);
				}
				
				if((type.equals("translate") && "boolean".equals(clazz)) || type.equals("boolean") ) {
					Element check = doc.createElement("check");
					check.setAttribute("key", tg.getOsmTagName());
					check.setAttribute("text", JSONTreeGenerator.getTagTitle(tg.getId(), dictionary));
					itemNode.appendChild(check);
				}
				else if(type.equals("translate")) {
					JSONObject tagValues = JSONTreeGenerator.getTagValues(tg.getClazz(), dictionary);
					if(tagValues != null) {
						Element combo = tg.isMultyValue() ? doc.createElement("multiselect") : doc.createElement("combo");
						combo.setAttribute("key", tg.getOsmTagName());
						combo.setAttribute("text", JSONTreeGenerator.getTagTitle(tg.getId(), dictionary));
						String delimitter = tg.isMultyValue() ? ";" : ",";
						
						String[] valuesWithTranslation = getValuesTranslation(
								tagValues, delimitter);
						
						combo.setAttribute("values", valuesWithTranslation[0]);
						combo.setAttribute("display_values", valuesWithTranslation[1]);
						
						itemNode.appendChild(combo);
					}
					
				}
			}
		}
		
		List<String> chids = parent2Childs.get(item.getName());
		if(chids != null && !chids.isEmpty()) {
			if(childGroupNode == null) {
				childGroupNode = doc.createElement("group");
				childGroupNode.setAttribute("name", JSONTreeGenerator.getTitle(item.getName(), dictionary)  + " (подробнее)");
			}
			
			for(String itemName : chids) {
				for(Node n : getPreset(catalog.get(itemName), catalog, parent2Childs, dictionary, doc)) {
					childGroupNode.appendChild(n);
				}
			}
		}

		if(itemNode != null) {
			result.add(itemNode);
		}

		if(childGroupNode != null) {
			result.add(childGroupNode);
		}
		
		return result;
		
	}

	private static String[] getValuesTranslation(JSONObject tagValues,
			String delimitter) {
		StringBuilder values = new StringBuilder();
		StringBuilder displayValues = new StringBuilder();
		
		@SuppressWarnings("unchecked")
		Iterator<String> keys = tagValues.keys();
		while(keys.hasNext()) {
			String key = (String) keys.next();
			String translation = tagValues.getString(key);
			
			values.append(delimitter).append(key);
			displayValues.append(delimitter).append(translation);
		}
		
		String valuesString = values.substring(1);
		String displayValuesString = displayValues.substring(1);
		
		String[] valuesWithTranslation = new String[]{valuesString, displayValuesString};
		return valuesWithTranslation;
	}

}
