package me.osm.gazetter.flap;

import java.io.File;
import java.io.IOException;

import me.osm.gazetter.utils.FileUtils;
import me.osm.gazetter.utils.FileUtils.LineHandler;

public class FlapPoiMatcher implements LineHandler {
	
	private String flapDumpPath;

	public FlapPoiMatcher(String flapDumpPath) {
		this.flapDumpPath = flapDumpPath;
	}
	
	public void run() {
		try {
			FileUtils.handleLines(new File(this.flapDumpPath), this);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
	}

	@Override
	public void handle(String s) {
		
	}
}
