package me.osm.gazetteer.web.utils;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.startsWith;
import static org.apache.commons.lang3.StringUtils.strip;
import static org.apache.commons.lang3.StringUtils.substringAfter;
import static org.apache.commons.lang3.StringUtils.substringBefore;
import groovy.lang.GroovyClassLoader;

import java.io.File;
import java.util.List;

import me.osm.gazetteer.web.imp.Replacer;

import org.apache.commons.lang3.StringUtils;

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

	private static class State {
		public StringBuilder sb = new StringBuilder();
		public String pattern = null;
		public String template = null;
		public boolean multiline = false;
	}
	
	public static void compile(List<Replacer> replacers,
			List<String> text) {
		
		State state = new State();
		
		for(String line : text) {
			if(!startsWith(line, "#") && !startsWith(line, "@")) {
				
				if(!state.multiline) {
					state.pattern = strip(substringBefore(line, "=>"));
					state.template = strip(substringAfter(line, "=>"));
				}
				
				if(startsWith(state.template, "///")) {
					if(!state.multiline) {
						state.multiline = true;
						state.template = substringAfter(state.template, "///");
						
						if(StringUtils.isNotBlank(state.template)) {
							state.sb.append(state.template);
						}
					}
					else {
						state.multiline = false;
						add(replacers, state.pattern, state.sb.toString());
						state.sb = new StringBuilder();
					}
				}
				else if(state.multiline) {
					state.sb.append(line).append("\n");
				}
				else {
					add(replacers, state.pattern, state.template);
				}
			}
			else if(startsWith(line, "@")) {
				String include = substringAfter(line, "include").trim();
				if(isNotBlank(include)) {
					
					compile(replacers, new File(include));
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
