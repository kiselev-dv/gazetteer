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

public class JoinSliceTest {

	private AddrJointHandler addrPointFormatter = new AddrPointFormatter();
	
	private class FakeOutStream extends OutputStream {

		@Override
		public void write(int b) throws IOException {
			
		}
		
	}
	
	public void test() {
		
		try {
			System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE");
			new File("/opt/osm/test.gjson").delete();
			
			JoinSliceTask task = new JoinSliceTask(addrPointFormatter, new File("/opt/osm/data/stripe0931.gjson"), 
					new ArrayList<JSONObject>(), new HashSet<String>(), null, null){
				
				@Override
				protected PrintWriter getOutWriter() throws FileNotFoundException {
					return new PrintWriter(new File("/opt/osm/test.gjson"));
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
