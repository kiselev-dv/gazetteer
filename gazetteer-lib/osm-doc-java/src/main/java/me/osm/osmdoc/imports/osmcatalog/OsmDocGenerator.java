package me.osm.osmdoc.imports.osmcatalog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
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
import me.osm.osmdoc.model.DocPart;
import me.osm.osmdoc.model.Feature;
import me.osm.osmdoc.model.KeyType;
import me.osm.osmdoc.model.MoreTags;
import me.osm.osmdoc.model.ObjectFactory;
import me.osm.osmdoc.model.Tags;
import me.osm.osmdoc.model.Tag.TagValueType;
import me.osm.osmdoc.model.Tag.Val;
import me.osm.osmdoc.model.Tag.Val.MatchType;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class OsmDocGenerator {
	private static final DocPartNSMapper DOC_PART_NS_MAPPER = new DocPartNSMapper();

	private static final String DOC_PART = "doc-part";
	static final String DOC_PART_NAMESPACE = "http://map.osm.me/osm-doc-part";
	private static final String ROOT_GROUP = "/";
	private static DocumentBuilderFactory docFactory;
	private static DocumentBuilder docBuilder;
	private static Properties properties;

	private static OsmDocGenerator instance;
	private static ObjectFactory objF;
	private static JAXBContext docPartJC;
	
	private static final Map<String, String> namespacesMapper = new HashMap<>();
	static {
		namespacesMapper.put(DOC_PART_NAMESPACE, "");
	}
	
	
	public static void main(String[] args) {
		
		//Oracle, thats why.
		System.setProperty("jsse.enableSNIExtension", "false");
		
		try {
			
			InputStream pis; 
			
			if(args.length == 1){
				System.out.println("Usage: OsmDocGenerator prorties.file");
				System.exit(1);

				File prop = new File(args[0]);
				pis = new FileInputStream(prop);
				if(!prop.exists()) {
					System.out.println("Cant open properties file");
					System.exit(1);
				}
			}
			else {
				pis = OsmDocGenerator.class.getClassLoader()
						.getResourceAsStream("./me/osm/osmdoc/imports/osmcatalog/default.properties");
			}
			
			
			properties = new Properties();
			properties.load(pis);

			instance = new OsmDocGenerator();
			docPartJC = JAXBContext.newInstance(DocPart.class);
			objF = new ObjectFactory();

			instance.generate();
			
		} catch (JSONException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (JAXBException e) {
			e.printStackTrace();
		}
	}

	private Document hierarchyDoc;
	private Element hierarchy;
	private Map<String, Element> hierarchyGroupNodes;
	private Map<String, List<String>> parent2Childs;
	private JSONObject dictionary;
	private LinkedHashMap<String, CatalogItem> catalog;
	
	private void generate() throws JSONException, FileNotFoundException, ParserConfigurationException {
		
		Set<String> exclude = JSONCatalogParser.getExclude(properties);
		
		JSONArray catalogJSON = new JSONArray(getContent("catalog.file"));
		
		JSONCatalogParser jsonCatalogParser = new JSONCatalogParser(catalogJSON, exclude, new HashSet<String>());
		catalog = jsonCatalogParser.parse();
		
		parent2Childs = jsonCatalogParser.getParent2Childs();
		
		dictionary = new JSONObject(getContent("dictionary.file"));
		
		docFactory = DocumentBuilderFactory.newInstance();
		docBuilder = docFactory.newDocumentBuilder();
 
		for(String itemName : parent2Childs.get(JSONCatalogParser.ROOT_ELEMENT_NAME)) {
			writeItem(catalog.get(itemName), ROOT_GROUP);
		}
		
		hierarchy.setAttribute("name", properties.getProperty("OsmMeXmlGenerator.hierarchyName"));
		writeOut(getHierarchyDoc(), 
				new File(properties.getProperty("OsmMeXmlGenerator.hierarchiesBaseDir") + "/" + 
						properties.getProperty("OsmMeXmlGenerator.hierarchyName") + ".xml"));
		
	}

	private String getContent(String property) {
		String pv = properties.getProperty(property);
		if(pv.startsWith("http")) {
			try {
				return IOUtils.toString(new URL(pv).openStream());
			} catch (MalformedURLException e) {
				throw new RuntimeException("can,t read " + property + " " + pv, e);
			} catch (IOException e) {
				throw new RuntimeException("can,t read " + property + " " + pv, e);
			}
		}

		return Utils.getFileContent(new File(pv));
	}

	private void writeItem(CatalogItem catalogItem, String path) {
		String newPath = path;
		
		if(catalogItem.getTags().isEmpty()) {
			newPath += catalogItem.getName() + "/";
			
			Element g = addHierarchyGroupNode(path, catalogItem.getName()); 
			g.setAttribute("name", catalogItem.getName());
			
			
			setTitle(catalogItem, dictionary, getHierarchyDoc(), g);
			setdescription(catalogItem, dictionary, getHierarchyDoc(), g);
			setWikiLink(catalogItem, dictionary, getHierarchyDoc(), g);
			setIcon(catalogItem, getHierarchyDoc(), g);
		}
		else
		{
			Feature feature = getFeature(catalogItem, dictionary, "ru");

			String featuresDir = StringUtils.stripToNull(properties.getProperty("OsmMeXmlGenerator.featuresBaseDir"));
			
			if(featuresDir != null) {
				File file = new File(featuresDir + path + catalogItem.getName() + ".xml");
				try {
					file.getParentFile().mkdirs();
					file.createNewFile();
				} catch (IOException e) {
					throw new RuntimeException("Не могу создать " + file.getPath(), e);
				}
				
				try {
					Document doc = docBuilder.newDocument();
					
					Marshaller marshaller = docPartJC.createMarshaller();
					marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
					marshaller.setProperty("com.sun.xml.bind.namespacePrefixMapper", DOC_PART_NS_MAPPER);
					
					DocPart docPart = objF.createDocPart();
					docPart.getFeature().add(feature);
					
					marshaller.marshal(docPart, doc);
					
					writeOut(doc, file);
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
			
			Element g = getHierarchyGroupNode(path); 
			Element fref = getHierarchyDoc().createElement("fref");
			g.appendChild(fref);
			
			fref.setAttribute("ref", catalogItem.getName());
		}
		
		List<String> childs = parent2Childs.get(catalogItem.getName());
		
		if(childs != null) {
			for(String child : childs) {
				writeItem(catalog.get(child), newPath);
			}
		}
	}

	private Element addHierarchyGroupNode(String path, String name) 
	{
		Element parent = getHierarchyGroupNode(path);
		String newPath = path + name + "/";
		
		Element groupElement = hierarchyDoc.createElement("group");
		parent.appendChild(groupElement);
		
		hierarchyGroupNodes.put(newPath, groupElement);
		
		return groupElement;
	}

	private Element getHierarchyGroupNode(String path) {
		
		if(hierarchyDoc == null) {
			createHierarchyDoc();
		}
		
		if(path.equals(ROOT_GROUP)) {
			return hierarchy;
		}
		
		if(hierarchyGroupNodes.get(path) == null) {
			return getHierarchyGroupNode(getSubPath(path));
		}
		
		return hierarchyGroupNodes.get(path);
		
	}

	private String getSubPath(String path) {
		List<String> parts = Arrays.asList(StringUtils.split(path, "/"));
		if(parts.isEmpty()) {
			return ROOT_GROUP;
		}
			
		return ROOT_GROUP + StringUtils.join(parts.subList(0, parts.size() - 1), '/'); 
	}

	private Document getHierarchyDoc() {
		if(hierarchyDoc == null) {
			createHierarchyDoc();
		}
		return hierarchyDoc;
	}

	private void createHierarchyDoc() {
		hierarchyDoc = docBuilder.newDocument();
		
		Element hierachies = hierarchyDoc.createElementNS(DOC_PART_NAMESPACE, DOC_PART);
		hierarchyDoc.appendChild(hierachies);		
		
		hierarchy = hierarchyDoc.createElement("hierarchy");
		hierachies.appendChild(hierarchy);
		
		hierarchyGroupNodes = new HashMap<String, Element>();
	}

	private Feature getFeature(CatalogItem catalogItem, JSONObject dictionary, String lang) {
		
		Feature feature = objF.createFeature();
		
		feature.setName(catalogItem.getName());
		String titleSRC = JSONTreeGenerator.getTitle(catalogItem.getName(), dictionary);
		feature.setTitle(titleSRC);
		
		String descriptionSRC = JSONTreeGenerator.getDescription(catalogItem.getName(), dictionary);
		if(StringUtils.isNotBlank(descriptionSRC)){
			feature.setDescription(descriptionSRC);
		}
		
		Tags mappedTags = mapTags(catalogItem.getTags(), lang);
		feature.getTags().add(mappedTags);
		setChildSpecificTags(catalogItem, feature, mappedTags);
		
		List<TagDescriptor> moreTags = catalogItem.getMoreTags();
		if(!moreTags.isEmpty()) {
			MoreTags moreTagsSrc = mapMoreTags(moreTags, lang);
			feature.setMoreTags(moreTagsSrc);
		}

		if(catalogItem.isPoi()) {
			Feature.Trait poiTrait = new Feature.Trait();
			poiTrait.setValue("poi");
			feature.getTrait().add(poiTrait);
		}

		setIcon(catalogItem, feature);
		
		String link = StringUtils.stripToNull(JSONTreeGenerator.getLink(catalogItem.getName(), dictionary));
		if(link != null) {
			feature.getWiki().add(link);
		}
		
		setApplience(catalogItem, feature);
		
		return feature;
	}

	@SuppressWarnings("unchecked")
	private MoreTags mapMoreTags(List<TagDescriptor> moreTags, String lang) {
		MoreTags result = objF.createMoreTags();
		for(TagDescriptor t : moreTags) {
			
			me.osm.osmdoc.model.Tag tag = objF.createTag();
			KeyType kt = objF.createKeyType();
			
			String tagTitleSRC = JSONTreeGenerator.getTagTitle(t.getId(), dictionary);
			
			if(StringUtils.isNoneBlank(tagTitleSRC)) {
				tag.setTitle(tagTitleSRC);
			}
			
			String tagType = t.getType();
			if(StringUtils.isNotBlank(tagType)) {
				if("translate".equals(tagType)) {
					tagType = "enum";
				}
				tag.setTagValueType(TagValueType.fromValue(tagType));
			}
			
			kt.setValue(t.getOsmTagName());
			tag.setKey(kt);

			JSONObject values = JSONTreeGenerator.getTagValues(t.getClazz(), dictionary);
			
			if(values != null) {
				for(String vk : (Set<String>)values.keySet()) {
					String valueTitleSRC = values.getString(vk);
					
					Val val = objF.createTagVal();
					tag.getVal().add(val);
					
					val.setValue(vk);
					val.setTitle(valueTitleSRC);
				}
			}
			
			result.getTag().add(tag);
		}
		
		return result;
	}

	private Tags mapTags(List<Tag> tags, String lang) {

		Tags result = objF.createTags();
		for(Tag t : tags) {
			
			me.osm.osmdoc.model.Tag tag = objF.createTag();
			KeyType kt = objF.createKeyType();
			String tagKey = t.getKey();
			
			String tagTitleSRC = JSONTreeGenerator.getTagTitle(tagKey, dictionary);
			
			if(StringUtils.isNoneBlank(tagTitleSRC)) {
				tag.setTitle(tagTitleSRC);
			}
			
			kt.setValue(tagKey);
			tag.setKey(kt);

			if(t.getValue().equals("*")) {
				kt.setMatch(MatchType.ANY);
			}
			
			Val val = objF.createTagVal();
			tag.getVal().add(val);
			
			val.setValue(t.getValue());
			
			result.getTag().add(tag);
		}
		
		return result;
	}

	private void setApplience(CatalogItem catalogItem, Feature feature) {
		if(catalogItem.getType() != null && !catalogItem.getType().isEmpty()) {
			for(String type :catalogItem.getType()) {
				if(type.equals("node")) {
					feature.getApplyedTo().add(me.osm.osmdoc.model.TagValueType.NODE);
				}
				if(type.equals("area")) {
					feature.getApplyedTo().add(me.osm.osmdoc.model.TagValueType.AREA);
				}
				if(type.equals("way")) {
					feature.getApplyedTo().add(me.osm.osmdoc.model.TagValueType.WAY);
				}
				if(type.equals("relation")) {
					feature.getApplyedTo().add(me.osm.osmdoc.model.TagValueType.RELATION);
				}
			}
		}
		
	}

	private void setChildSpecificTags(CatalogItem catalogItem,
			Feature feature, Tags mappedTags) {
		
		Map<String, Set<String>> tags = tagsAsMap(catalogItem.getTags());
		
		List<Tag> childTagsList = new ArrayList<>();
		List<CatalogItem> childsDFS = childsDFS(catalogItem, new ArrayList<CatalogItem>());
		for(CatalogItem child : childsDFS)
		{
			if(!catalogItem.equals(child) && child.getTags() != null) {
				childTagsList.addAll(child.getTags());
			}
		}
		
		Set<String> childsEmptyKeys = new HashSet<>();
		Map<String, Set<String>> childTags = tagsAsMap(childTagsList);
		for(Entry<String, Set<String>> tag : tags.entrySet()) {
			String key = tag.getKey();
			Set<String> childTagsSet = childTags.get(key);
			if(childTagsSet != null) {
				childTagsSet.removeAll(tag.getValue());
				if(childTagsSet.isEmpty()) {
					childsEmptyKeys.add(key);
				}
			}
		}
		
		for(String emptyKey : childsEmptyKeys) {
			childTags.remove(emptyKey);
		}
		
		for(Entry<String, Set<String>> tag : childTags.entrySet()) {
			me.osm.osmdoc.model.Tag tg = objF.createTag();
			tg.setExclude(true);
			
			KeyType kt = objF.createKeyType();
			kt.setValue(tag.getKey());
			tg.setKey(kt);
			
			List<String> vals = new ArrayList<>(tag.getValue());
			Collections.sort(vals);
			for(String val : vals) {
				Val tv = objF.createTagVal();
				tv.setValue(val);
			}
			
			mappedTags.getTag().add(tg);
		}
		
	}

	private Map<String, Set<String>> tagsAsMap(List<Tag> list) {
		Map<String, Set<String>> result = new HashMap<>();
		for(Tag t : list)	{
			if(result.get(t.getKey()) == null) {
				result.put(t.getKey(), new HashSet<String>());
			}
			result.get(t.getKey()).add(t.getValue());
		}
		return result;
	}

	private List<CatalogItem> childsDFS(CatalogItem catalogItem, ArrayList<CatalogItem> arrayList) {
		
		List<String> list = parent2Childs.get(catalogItem.getName());
		if(list != null) {
			for(String s : list)
			{
				childsDFS(catalog.get(s), arrayList);
			}
		}
		
		if(catalogItem != null) {
			arrayList.add(catalogItem);
		}
		
		return arrayList;
	}

	private void setIcon(CatalogItem catalogItem, Feature feature) {
		
		String iconsDir = properties.getProperty("OsmMeXmlGenerator.iconsDir");
		if(iconsDir != null && new File(iconsDir + "/" + catalogItem.getName() + ".png").exists()) {
			
			String urlBase = StringUtils.stripToEmpty(properties.getProperty("OsmMeXmlGenerator.iconsUrlBase"));
			
			feature.getIcon().add(urlBase + catalogItem.getName() + ".png");
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
	
	private void setTitle(CatalogItem catalogItem, JSONObject dictionary,
			Document document, Element parent) {
		
		Element titleNode = document.createElement("title");
		titleNode.appendChild(document.createTextNode(JSONTreeGenerator.getTitle(catalogItem.getName(), dictionary)));
		parent.appendChild(titleNode);
		
	}
	
	private void setWikiLink(CatalogItem catalogItem, JSONObject dictionary,
			Document document, Element feature) {
		
		String link = StringUtils.stripToNull(JSONTreeGenerator.getLink(catalogItem.getName(), dictionary));
		if(link != null) {
			Element wikiElement = document.createElement("wiki");
			wikiElement.appendChild(document.createTextNode(link));
			feature.appendChild(wikiElement);
		}
		
	}

	private void setIcon(CatalogItem catalogItem, Document document,
			Element feature) {
		
		String iconsDir = properties.getProperty("OsmMeXmlGenerator.iconsDir");
		if(iconsDir != null && new File(iconsDir + "/" + catalogItem.getName() + ".png").exists()) {
			
			String urlBase = StringUtils.stripToEmpty(properties.getProperty("OsmMeXmlGenerator.iconsUrlBase"));
			
			Element iconElement = document.createElement("icon");
			iconElement.appendChild(document.createTextNode(urlBase + catalogItem.getName() + ".png"));
			feature.appendChild(iconElement);
		}
		
	}

	private void setdescription(CatalogItem catalogItem, JSONObject dictionary,
			Document document, Element feature) {
		
		Element descriptionNode = document.createElement("description");
		String description = JSONTreeGenerator.getDescription(catalogItem.getName(), dictionary);
		if(StringUtils.stripToNull(description) != null) {
			descriptionNode.appendChild(document.createTextNode(description));
			feature.appendChild(descriptionNode);
		}
		
	}
}
