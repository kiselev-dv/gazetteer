package me.osm.osmdoc.processing;

import java.util.Locale;

import me.osm.osmdoc.localization.L10n;
import me.osm.osmdoc.read.OSMDocFacade;

public class CheckL10n {

	private String catalog;
	private String properties;

	public CheckL10n(String catalog, String properties) {
		this.catalog = catalog;
		this.properties = properties;
	}

	public void run() {
		L10n.setCatalogPath(properties);
		OSMDocFacade osmDocFacade = new OSMDocFacade(catalog);
		osmDocFacade.listTranslatedFeatures(Locale.forLanguageTag("ru"));
	}

}
