package me.osm.gazetteer.web.imp;

import me.osm.gazetteer.web.utils.TranslitUtil;

public class ApacheASCIIFoldTransliterator implements Transliterator {

	@Override
	public String transliterate(String input) {
		return TranslitUtil.translit(input);
	}

}
