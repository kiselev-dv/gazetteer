package me.osm.gazetter.join.out_handlers;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;

import me.osm.gazetter.utils.FileUtils;

public abstract class SingleWriterJOHBase implements JoinOutHandler {

	protected PrintWriter writer = new PrintWriter(System.out);
	
	private static final Object mutex = new Object();
	
	@Override
	public JoinOutHandler newInstance(List<String> options) {

		if(!options.isEmpty()) {
			initializeWriter(options.get(0));
		}
		
		return this;
	}

	protected void initializeWriter(String string) {
		try {
			if(string != null && !string.equals("-")) {
				writer = FileUtils.getPrintWriter(new File(string), false);
			}
		}
		catch(Exception e) {
			throw new RuntimeException(e);
		}
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
	
	public void flush() {
		writer.flush();
	}


}
