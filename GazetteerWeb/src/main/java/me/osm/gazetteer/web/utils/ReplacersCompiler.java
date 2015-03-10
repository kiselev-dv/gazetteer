package me.osm.gazetteer.web.utils;

import groovy.lang.GroovyClassLoader;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import static org.apache.commons.lang3.StringUtils.*;
import me.osm.gazetteer.web.imp.Replacer;

public class ReplacersCompiler {

	private final static GroovyClassLoader gcl = new GroovyClassLoader(ReplacersCompiler.class.getClassLoader());
	private static final ReplacersFactory replacersFactory;
	static {
		gcl.addClasspath("lib");
		
		try {
			Class<?> clazz = gcl.parseClass(new File("config/replacers/ReplacersFactory.groovy"));
			Object aScript = clazz.newInstance();
			if(aScript instanceof ReplacersFactory) {
				replacersFactory = (ReplacersFactory) aScript;
			}
			else {
				throw new RuntimeException("Can't load ReplacersFactory");
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public static void compile(List<Replacer> replacers, File src) {

		List<String> configContent = FileUtils.readLines(src);
		compile(replacers, configContent);
	}

	public static void compile(List<Replacer> replacers,
			List<String> text) {
		
		boolean multiline = false;
		
		StringBuilder sb = new StringBuilder();
		String pattern = null;
		String template = null;
		
		for(String line : text) {
			if(!startsWith(line, "#")) {
				
				if(!multiline) {
					pattern = strip(substringBefore(line, "=>"));
					template = strip(substringAfter(line, "=>"));
				}
				
				if(startsWith(template, "///")) {
					if(!multiline) {
						multiline = true;
						template = substringAfter(template, "///");
						
						if(StringUtils.isNotBlank(template)) {
							sb.append(template);
						}
					}
					else {
						multiline = false;
						add(replacers, pattern, sb.toString());
						sb = new StringBuilder();
					}
				}
				else if(multiline) {
					sb.append(line).append("\n");
				}
				else {
					add(replacers, pattern, template);
				}
			}
		}
	}

	private static void add(List<Replacer> replacers, String pattern,
			String template) {
		try {
			replacers.add(replacersFactory.createReplacer(pattern, template));
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
}
