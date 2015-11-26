package me.osm.gazetter.join.out_handlers;

import java.io.BufferedWriter;
import java.io.IOException;

import org.apache.commons.io.output.NullWriter;

import me.osm.gazetter.utils.FileUtils.LineHandler;

/**
 * Allow to pass line handler into external libraries
 * 
 * <b>IMPORTANT</b> this feature is experimental, and
 * work only with ExternalSorting lib.
 *  
 * */
public final class FakeWriter extends BufferedWriter {
	
	private LineHandler hadler;
	
	/**
	 * @param hadler will be called for every line
	 */
	public FakeWriter(LineHandler hadler) {
		super(new NullWriter());
		
		this.hadler = hadler;
	}

	@Override
	public void write(String str) throws IOException {
		hadler.handle(str);
	}

	@Override
	public void newLine() throws IOException {
		
	}

	@Override
	public void flush() throws IOException {

	}

	@Override
	public void close() throws IOException {

	}
}