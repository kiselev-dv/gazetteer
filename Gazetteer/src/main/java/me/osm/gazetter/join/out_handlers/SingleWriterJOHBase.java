package me.osm.gazetter.join.out_handlers;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;

import me.osm.gazetter.utils.FileUtils;

public abstract class SingleWriterJOHBase implements JoinOutHandler {

	private PrintWriter writer = new PrintWriter(System.out);
	
	private static final Object mutex = new Object();
	
	@Override
	public JoinOutHandler newInstance(List<String> options) {
		try {
			if(!options.isEmpty()) {
				writer = FileUtils.getPrintwriter(new File(options.get(0)), false);
			}
		}
		catch(Exception e) {
			throw new RuntimeException(e);
		}
		
		return this;
	}
	
	protected void println(String s) {
		synchronized (mutex) {
			writer.println(s);
		}
	}
	
	@Override
	public void stripeDone(String stripe) {
		writer.flush();
	}
	
	@Override
	public void allDone() {
		writer.flush();
		writer.close();
	}


}
