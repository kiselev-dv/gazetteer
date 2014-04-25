package me.osm.gazetter.test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;

import me.osm.gazetter.join.AddrJointHandler;
import me.osm.gazetter.join.AddrPointFormatter;
import me.osm.gazetter.join.JoinSliceTask;

import org.json.JSONObject;
import org.junit.Test;

public class JoinSliceTest {

	private AddrJointHandler addrPointFormatter = new AddrPointFormatter();
	
	private class FakeOutStream extends OutputStream {

		@Override
		public void write(int b) throws IOException {
			
		}
		
	}
	
	@Test
	public void test() {
		try {
			JoinSliceTask task = new JoinSliceTask(addrPointFormatter, new File("/opt/osm/data/stripe2197.gjson"), new ArrayList<JSONObject>(), new HashSet<String>(), null){
				@Override
				protected PrintWriter getOutWriter() throws FileNotFoundException {
					return new PrintWriter(new FakeOutStream());
				}
			};
			
			task.run();
		}
		catch (Throwable e) {
			e.printStackTrace();
			throw e;
		}
	}

}
