package me.osm.gazetter.out;

import java.io.File;

import me.osm.gazetter.join.Joiner;
import me.osm.gazetter.utils.FileUtils;

public class OutWriter {
	
	private OutConverter converter;
	private String slicesDir;

	public OutWriter(String slicesDir, OutConverter converter) {
		this.converter = converter;
		this.slicesDir = slicesDir;
	}
	
	public void write() {
		File folder = new File(slicesDir);
		for(File stripeF : folder.listFiles(Joiner.STRIPE_FILE_FN_FILTER)) {
			FileUtils.handleLines(stripeF, converter);
		}
		
		converter.close();
	}
}
