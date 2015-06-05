package me.osm.gazetteer.web.utils;

import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;

public class TranslitUtil {
	
	public static String translit(String s) {
		
		char[] out = new char[s.length() * 4];
		int count = ASCIIFoldingFilter.foldToASCII(s.toCharArray(), 0, out, 0, s.length());
		
		return new String(out, 0, count);
	}
	
}
