package me.osm.gazetter.join.out_handlers;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import me.osm.gazetter.utils.FileUtils;

public abstract class SingleWriterJOHBase implements JoinOutHandler {

	protected PrintWriter writer = new PrintWriter(System.out);
	
	private static final Object mutex = new Object();
	
	@Override
	public JoinOutHandler initialize(HandlerOptions options) {

		String out = options.getString("out", "-");
		
		initializeWriter(out);
		
		return this;
	}

	public HandlerOptions parseHandlerOptions(List<String> options) {
		List<String> defOptions = new ArrayList<>();
		defOptions.add("out");
		return HandlerOptions.parse(options, getHandlerArguments(defOptions));
	};

	protected Collection<String> getHandlerArguments(Collection<String> defOptions) {
		return defOptions;
	}

	protected void initializeWriter(String out) {
		try {
			
			if(out != null && !out.equals("-")) {
				writer = FileUtils.getPrintWriter(new File(out), false);
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
