package me.osm.gazetteer.web.imp;


/**
 * Transliterator - provides service for 
 * romanization, ASCII folding and so on.
 * 
 * */
public interface Transliterator {
	public String transliterate(String input);
}
