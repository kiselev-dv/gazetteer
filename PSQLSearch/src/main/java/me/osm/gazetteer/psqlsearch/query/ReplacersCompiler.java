package me.osm.gazetteer.psqlsearch.query;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.startsWith;
import static org.apache.commons.lang3.StringUtils.strip;
import static org.apache.commons.lang3.StringUtils.substringAfter;
import static org.apache.commons.lang3.StringUtils.substringBefore;

import java.io.File;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import groovy.lang.GroovyClassLoader;

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
		try {
			List<String> configContent = FileUtils.readLines(src);
			compile(replacers, configContent);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
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
			if(!startsWith(line, "#") && !startsWith(line, "@") && isNotBlank(line)) {
				
				if(!state.multiline) {
					state.pattern = strip(substringBefore(line, "=>"));
					state.template = strip(substringAfter(line, "=>"));
				}
				
				if(startsWith(state.template, "///") || startsWith(line, "///")) {
					if(!state.multiline) {
						state.multiline = true;
						state.template = substringAfter(state.template, "///");
						state.sb = new StringBuilder(state.template);
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
					if (StringUtils.endsWith(include, "*")) {
						File folder = new File(StringUtils.substringBeforeLast(include, "*"));
						if (folder.isDirectory()) {
							for(File f : folder.listFiles()) {
								if (!f.isDirectory()) {
									compile(replacers, f);
								}
							}
						}
					}
					else {
						compile(replacers, new File(include));
					}
				}
			}
		}
	}

	private static void add(List<Replacer> replacers, String pattern,
			String template) {
		try {
			if(StringUtils.isNotBlank(pattern)) {
				replacers.add(replacersFactory.createReplacer(pattern, template));
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
}
