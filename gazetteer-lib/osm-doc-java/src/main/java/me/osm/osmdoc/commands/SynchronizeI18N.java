package me.osm.osmdoc.commands;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;

import me.osm.osmdoc.localization.L10n;
import me.osm.osmdoc.model.Choise;
import me.osm.osmdoc.model.Feature;
import me.osm.osmdoc.model.Group;
import me.osm.osmdoc.model.Hierarchy;
import me.osm.osmdoc.model.MoreTags;
import me.osm.osmdoc.model.Tag;
import me.osm.osmdoc.model.Trait;
import me.osm.osmdoc.model.Tag.Val;
import me.osm.osmdoc.read.DOCFileReader;
import me.osm.osmdoc.read.DOCFolderReader;
import me.osm.osmdoc.read.DOCReader;

import org.apache.commons.lang3.StringUtils;

public class SynchronizeI18N {
	
	private static DOCReader reader;
	private static Locale lang;
	private static Properties properties;
	private static FileOutputStream os;
	
	public static void main(String[] args) {
		run("/opt/osm/osm-doc/catalog", "ru");
	}
	
	public static void run(String docPath, String l) {
		
		lang = Locale.forLanguageTag(l);
		
		if(docPath.endsWith(".xml") || docPath.equals("jar")) {
			reader = new DOCFileReader(docPath);
		}
		else {
			reader = new DOCFolderReader(docPath);
		}
		
		try {
			properties = new Properties();

			for(Feature f : reader.getFeatures()) {
				check(f);
			}
			
			for(Hierarchy h : reader.listHierarchies()) {
				check(h);
			}
			
			for(Trait t : reader.getTraits().values()) {
				check(t);
			}
			
			os = new FileOutputStream(new File(docPath + "/strings_ru.properties"));
			properties.store(os, new Date().toString());
			os.flush();
			os.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}

	private static void check(Trait t) {
		check(t.getTitle());
		check(t.getDescription());
		
		MoreTags moreTags = t.getMoreTags();
		if(moreTags != null) {
			for(Tag tg : moreTags.getTag()) {
				check(tg);
			}
			
			for(Choise ch : moreTags.getChoise()) {
				for(Tag tg : ch.getTag()) {
					check(tg);
				}
			}
		}
	}

	private static void check(Tag tg) {
		check(tg.getTitle());
		check(tg.getDescription());
		
		for(Val v : tg.getVal()) {
			check(v);
		}
	}

	private static void check(Val v) {
		check(v.getTitle());
		check(v.getDescription());
	}

	private static void check(Hierarchy h) {
		check(h.getName());
		
		for(Group g : h.getGroup()) {
			check(g);
		}
	}

	private static void check(Group g) {
		check(g.getTitle());
		check(g.getDescription());
		
		for(Group cg : g.getGroup()) {
			check(cg);
		}
		
	}

	private static void check(Feature f) {
		check(f.getTitle());
		check(f.getDescription());
		
		if(f.getMoreTags() != null) {
			for(Tag tg : f.getMoreTags().getTag()) {
				check(tg);
			}
		}
	}

	private static void check(String s) {
		s = StringUtils.stripToNull(s);
		if(s != null) {
			String trOrNull = L10n.trOrNull(s, lang);
			if(trOrNull == null) {
				properties.setProperty(s, "");
			}
			else {
				properties.setProperty(s, trOrNull);
			}
		}
	}
	
}
