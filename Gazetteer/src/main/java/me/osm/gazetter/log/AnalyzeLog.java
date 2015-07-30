package me.osm.gazetter.log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import me.osm.gazetter.utils.FileUtils;

public class AnalyzeLog implements FileUtils.LineHandler {

	private List<String> logs;
	private String out;

	private List<GazetteerLogMessage> errors = new ArrayList<>();
	
	public AnalyzeLog(String out, List<String> logs) {
		this.out = out;
		this.logs = logs;
	}

	public void run() {
		
		try {
			for(String log : logs) {
				FileUtils.handleLines(new File(log), this);
			}
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	@Override
	public void handle(String s) {
		
	}

}
