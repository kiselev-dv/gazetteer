package me.osm.osmdoc.commands;

import static me.osm.osmdoc.localization.L10n.L10N_PREFIX;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.Properties;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import me.osm.osmdoc.imports.osmcatalog.DocPartNSMapper;
import me.osm.osmdoc.model.DocPart;
import me.osm.osmdoc.model.Feature;
import me.osm.osmdoc.model.Group;
import me.osm.osmdoc.model.Hierarchy;
import me.osm.osmdoc.model.KeyType;
import me.osm.osmdoc.model.Tag;
import me.osm.osmdoc.model.Tags;
import me.osm.osmdoc.model.Tag.Val;

import org.apache.commons.lang3.StringUtils;

public class ExpStrings {

	private static final DocPartNSMapper DOC_PART_NS_MAPPER = new DocPartNSMapper();
	
	private String catalogPath;
	private Properties properties;

	public ExpStrings(String catalogPath) {
		this.catalogPath = catalogPath;
	}

	public void run() {
		properties = new Properties();
		
		File root = new File(catalogPath);
		iterateOverFiles(root);
		
		try {
			OutputStream os = new FileOutputStream(new File(catalogPath + "/strings_ru.properties"));
			properties.store(os, new Date().toString());
			os.flush();
			os.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void iterateOverFiles(File root) {
		if(root.isFile()) {
			if(root.getName().endsWith(".xml")) {
				parse(root);
			}
		}
		else if(root.isDirectory()){
			for(File f : root.listFiles()) {
				iterateOverFiles(f);
			}
		}
	}

	private void parse(File root) {
		
		try {
			JAXBContext jaxbContext = JAXBContext.newInstance("me.osm.osmdoc.model", 
					me.osm.osmdoc.model.ObjectFactory.class.getClassLoader());
			
			Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
			
			DocPart doc = (DocPart) unmarshaller.unmarshal(root);
			
			process(doc);
			
			Marshaller marshaller = jaxbContext.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
			marshaller.setProperty("com.sun.xml.bind.namespacePrefixMapper", DOC_PART_NS_MAPPER);
			marshaller.marshal(doc, root);
			
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void process(DocPart doc) {
		
		for(Feature f : doc.getFeature()) {
			processFeature(f);
		}
		
		for (Hierarchy h : doc.getHierarchy()) {
			processHierarchy(h);
		}
	}

	private void processHierarchy(Hierarchy h) {
		for(Group g : h.getGroup()) {
			
			String gTitle = g.getTitle();
			
			if(!gTitle.startsWith(L10N_PREFIX)) {
				String i18nKey = L10N_PREFIX + "group." + h.getName() + "." + g.getName() + ".title";
				properties.setProperty(i18nKey, gTitle);
				g.setTitle(i18nKey);
			}

			String gDesc = g.getDescription();
			
			if(gDesc != null && !gDesc.startsWith(L10N_PREFIX)) {
				String i18nKey = L10N_PREFIX + "group." + h.getName() + "." + g.getName() + ".description";
				properties.setProperty(i18nKey, gDesc);
				g.setDescription(i18nKey);
			}
		}
	}

	private void processFeature(Feature f) {
		String name = f.getName();
		
		if(!f.getTitle().startsWith(L10N_PREFIX)) {
			String i18nKey = L10N_PREFIX + "feature." + name + ".title";
			properties.setProperty(i18nKey, f.getTitle());
			f.setTitle(i18nKey);
		}
		
		if(StringUtils.stripToNull(f.getDescription()) != null 
				&& !f.getDescription().startsWith(L10N_PREFIX)) {
			
			String i18nKey = L10N_PREFIX + "feature." + name + ".description";
			properties.setProperty(i18nKey, f.getDescription());
			f.setDescription(i18nKey);
			
		}
		
		processTags(f, name);
		processMoreTags(f, name);
	}

	private void processTags(Feature f, String name) {
		
		for(Tags sinonym : f.getTags()) {
			for(Tag tag : sinonym.getTag()) {
				processTag(name, tag, "tags");
			}
		}
	}

	private void processMoreTags(Feature f, String name) {
		if(f != null && f.getMoreTags() != null) {
			for(Tag tag : f.getMoreTags().getTag()) {
				processTag(name, tag, "more_tags");
			}
		}
	}

	private void processTag(String name, Tag tag, String tagI18nKey) {
		KeyType key = tag.getKey();
		
		String title = tag.getTitle();
		if(title!= null && !title.startsWith(L10N_PREFIX)) {
			String i18nKey = L10N_PREFIX + "feature." + name + "." + tagI18nKey + "." + key.getValue() + ".title";
			properties.setProperty(i18nKey, title);
			
			tag.setTitle(i18nKey);
		}
		
		String description = tag.getDescription();
		if(description!= null && !description.startsWith(L10N_PREFIX)) {
			String i18nKey = L10N_PREFIX + "feature." + name + "." + tagI18nKey + "."+ key.getValue() + ".description";
			properties.setProperty(i18nKey, description);
			
			tag.setTitle(i18nKey);
		}
		
		for(Val val : tag.getVal()) {
			String valueKey = val.getValue();
			String valTitle = val.getTitle();
			String valDesc = val.getDescription();
			
			if(valTitle != null && !valTitle.startsWith(L10N_PREFIX)) {
				String i18nKey = L10N_PREFIX + "feature." + name + "." + tagI18nKey + "."+ key.getValue() + "." + valueKey + ".title";
				properties.setProperty(i18nKey, valTitle);
				val.setTitle(i18nKey);
			}

			if(valDesc != null && !valDesc.startsWith(L10N_PREFIX)) {
				String i18nKey = L10N_PREFIX + "feature." + name + "." + tagI18nKey + "."+ key.getValue() + "." + valueKey + ".description";
				properties.setProperty(i18nKey, valDesc);
				val.setDescription(i18nKey);
			}
		}
	}

}
