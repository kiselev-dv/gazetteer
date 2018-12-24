package me.osm.osmdoc.localization;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class L10n {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(L10n.class);
	public static final String L10N_PREFIX = "l10n.";
	public static final Set<String> supported = new HashSet<String>(
			Arrays.asList("ru", "en"));
	
	private static String catalogPath = null;
	
	private L10n() {
		
	}
	
	private L10n(Locale locale) {
		if(catalogPath == null) {
			URL[] urls = {L10n.class.getClassLoader().getResource("l10n")};
			strings = ResourceBundle.getBundle("strings", locale, 
					new URLClassLoader(urls, L10n.class.getClassLoader()));
		}
		else {
			// Access to rbLoader isn't safe here
			strings = ResourceBundle.getBundle("strings", locale, rbLoader);
		}
	}

	private ResourceBundle strings;
	
	private static final Map<String, L10n> instances = new HashMap<String, L10n>();
	private static ClassLoader rbLoader; 
	
	public static String trOrNull(String key, Locale locale) {
		
		if(locale == null) {
			locale = Locale.getDefault();
		}
		
		if(key != null && key.startsWith(L10N_PREFIX)) {
			
			if(!supported.contains(locale.toLanguageTag())) {
				return null;
			}
			
			if(instances.get(locale.getDisplayName()) == null) {
				synchronized (instances) {
					if(instances.get(locale.getDisplayName()) == null) {
						instances.put(locale.getDisplayName(), new L10n(locale));
					}
				}
			}
			
			if(instances.get(locale.getDisplayName()).strings.containsKey(key)) {
				return instances.get(locale.getDisplayName()).strings.getString(key);
			}
			else {
				return null;
			}
		}

		return key;
		
	}
	
	public static String tr(String key, Locale locale) {
		
		String result = trOrNull(key, locale);
		
		if(result == null && StringUtils.isNotEmpty(key)) {
			LOGGER.warn("Localization key: {} not found for locale {}.", key, locale);
			return key;
		}
		
		return result;
		
	}
	
	public static synchronized void setCatalogPath(String path) {
		catalogPath = path;
		
		try {
			
			File catalogFile = null;
			
			File file = new File(catalogPath);
			
			if(containProperties(file)) {
				catalogFile = file;
			}
			else {
				catalogFile = getL10nFolder(file); 
				if(catalogFile == null) {
					catalogFile = getL10nFolder(file.getParentFile());
				}
			}
			
			if(catalogFile == null) {
				catalogPath = null;
				return;
			}
			
			LOGGER.info("Initialize L10n from {}", catalogFile);
			
			URL[] urls = {catalogFile.toURI().toURL(), L10n.class.getClassLoader().getResource("l10n")};
			rbLoader = new URLClassLoader(urls, L10n.class.getClassLoader());
		}
		catch (Throwable t) {
			t.printStackTrace();
			catalogPath = null;
		}
	}

	private static File getL10nFolder(File f) {
		for(File file : f.listFiles()) {
			if("l10n".equals(file.getName())) {
				return file;
			}
		}
		return null;
	}

	private static boolean containProperties(File file) {
		for(String n : file.list()) {
			if(n.endsWith(".properties")) {
				return true;
			}
		}
		return false;
	}
}
