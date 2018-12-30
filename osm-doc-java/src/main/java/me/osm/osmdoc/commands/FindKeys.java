package me.osm.osmdoc.commands;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import me.osm.osmdoc.localization.L10n;
import me.osm.osmdoc.model.Feature;
import me.osm.osmdoc.model.Group;
import me.osm.osmdoc.model.Hierarchy;
import me.osm.osmdoc.model.MoreTags;
import me.osm.osmdoc.model.Tag;
import me.osm.osmdoc.model.Trait;
import me.osm.osmdoc.model.Tag.Val;
import me.osm.osmdoc.read.DOCReader;
import me.osm.osmdoc.read.OSMDocFacade;
import me.osm.osmdoc.util.CountryByCode;

import org.apache.commons.lang3.StringUtils;

public class FindKeys {

	private String outPath;
	private String catalogPath;

	private OSMDocFacade osmDocFacade;
	private List<String> langs;
	
	private Map<String, Properties> prop = new HashMap<>();

	public FindKeys(String catalog, String out, List<String> langs) {
		this.catalogPath = catalog;
		this.outPath = out;
		this.langs = langs;

		osmDocFacade = new OSMDocFacade(this.catalogPath);
	}

	public void run() {
		DOCReader reader = osmDocFacade.getReader();
		
		for(Hierarchy h : reader.listHierarchies()) {
			for(Group g : h.getGroup()) {
				traverseHGroup(g, h);
			}
		}
		
		Map<String, Trait> traits = reader.getTraits();
		for(Entry<String, Trait> entry : traits.entrySet()) {
			writeTrait(entry);
		}
		
		List<Feature> features = reader.getFeatures();
		for(Feature feature : features) {
			writeFeature(feature);
		}
		
		save();
	}

	private void writeFeature(Feature feature) {
		String name = feature.getName();
		
		String keyPrefix = L10n.L10N_PREFIX + "feature." + name;
		String titleKey = keyPrefix + ".title";

		String title = feature.getTitle();
		
		writeTitle("feature", name, title, titleKey);
		
		MoreTags moreTags = feature.getMoreTags();
		if(moreTags != null) {
			writeMoreTags("feature_tags", moreTags, keyPrefix);
		}
		
	}

	private void writeTrait(Entry<String, Trait> entry) {
		String name = entry.getKey();
		
		String keyPrefix = L10n.L10N_PREFIX + "trait." + name;
		String titleKey = keyPrefix + ".title";

		Trait trait = entry.getValue();
		String title = trait.getTitle();
		
		writeTitle("trait", name, title, titleKey);
		
		MoreTags moreTags = trait.getMoreTags();
		if(moreTags != null) {
			writeMoreTags("trait", moreTags, keyPrefix);
		}
	}

	private void writeMoreTags(String type, MoreTags moreTags, String keyPrefix) {
		List<Tag> tag = moreTags.getTag();
		if(tag != null) {
			for(Tag t : tag) {
				String tagKey = t.getKey().getValue();
				String title = t.getTitle();
				String tagKeyPrefix = keyPrefix + ".tag." + tagKey;
				
				tagKey = tagKeyPrefix + ".title";
				if(title.startsWith(L10n.L10N_PREFIX)) {
					tagKey = title;
				}
				
				writeTitle(type, t.getKey().getValue(), title, tagKey);
				
				List<Val> val = t.getVal();
				if(val != null) {
					for(Val v : val) {
						String valName = v.getValue();
						String valKey = tagKeyPrefix + "-" + valName + ".title";
						String valTitle = v.getTitle();
						
						if(valTitle.startsWith(L10n.L10N_PREFIX)) {
							valKey = valTitle;
						}
						
						if("true".equals(valName) || "yes".equals(valName)) {
							valKey = L10n.L10N_PREFIX + "more_tags.true";
							valTitle = "Да";
						}

						if("false".equals(valName) || "no".equals(valName)) {
							valKey = L10n.L10N_PREFIX + "more_tags.false";
							valTitle = "Нет";
						}
						
						writeTitle(type, valName, valTitle, valKey);
					}
				}
			}
		}
	}

	private void traverseHGroup(Group g, Hierarchy h) {
		String title = g.getTitle();

		String key = L10n.L10N_PREFIX + "group." + h.getName() + "." + g.getName() + ".title";
		
		writeTitle("hierarchy", g.getName(), title, key);
		
		if(g.getGroup() != null) {
			for(Group g2 : g.getGroup()) {
				traverseHGroup(g2, h);
			}
		}
	}

	private String writeTitle(String type, String name, String title, String key) {
		
		if(StringUtils.startsWith(title, L10n.L10N_PREFIX)) {
		
			for(String l : langs) {
				
				String tr = L10n.trOrNull(title, Locale.forLanguageTag(l));
				if(tr != null) {
					write(type, l, title, tr);
				}
				else if("en".equals(l)) {
					
					if(key.contains(".country.") && name.length() < 3) {
						String cc = CountryByCode.get(name);
						name = (cc != null ? cc : name);
					}

					write(type, "en", key, formatName(name));
				}
			}
			
		}
		else {
			
			System.err.println(name + " title isn't translated " + title);
			
			write(type, "ru", key, formatName(title));
			write(type, "en", key, formatName(name));
		}
		
		return key;
	}

	private String formatName(String name) {
		return StringUtils.replaceChars(StringUtils.capitalize(name), "_:", "  ");
	}

	private void write(String type, String lang, String key, String value) {
		
		String propKey = type + "_" + lang;
		
		if(prop.get(propKey) == null) {
			prop.put(propKey, new Properties());
		}
		
		Properties properties = prop.get(propKey);

		properties.put(key, value);
	}
	
	private void save() {
		
		for(Entry<String, Properties> entry : prop.entrySet()) {
			String name = entry.getKey();
			
			try {
				OutputStream os = new FileOutputStream(new File(outPath + "/" + name + ".properties"));
				entry.getValue().store(os, new Date().toString());
				os.flush();
				os.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		try {
			
			OutputStream ru = new FileOutputStream(new File(outPath + "/strings_ru.properties"));
			OutputStream en = new FileOutputStream(new File(outPath + "/strings_en.properties"));
			
			for(Entry<String, Properties> entry : prop.entrySet()) {
				String name = entry.getKey();
				if(StringUtils.endsWith(name, "ru")) {
					entry.getValue().store(ru, new Date().toString());
				}
				if(StringUtils.endsWith(name, "en")) {
					entry.getValue().store(en, new Date().toString());
				}
			}
			
			ru.flush();
			ru.close();

			en.flush();
			en.close();
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
