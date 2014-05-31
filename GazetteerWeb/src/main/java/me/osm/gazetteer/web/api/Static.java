package me.osm.gazetteer.web.api;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jboss.netty.buffer.ChannelBuffers;
import org.restexpress.Request;
import org.restexpress.Response;

public class Static {
	
	public void read(Request req, Response res)	{
		String path = req.getPath();
		path = StringUtils.remove(path, "..");
		path = "static/" + StringUtils.remove(path, "/static");
		
		try	{
			File file = new File(path);
			
			if(file.exists()) {
				String mime = Files.probeContentType(Paths.get(file.getPath()));
				res.setContentType(mime);
				res.setBody(ChannelBuffers.wrappedBuffer(IOUtils.toByteArray(new FileInputStream(file))));
			}
			else {
				res.setResponseCode(404);
			}
			
		}
		catch (Exception e) {
			res.setException(e);
		} 
	}
	
}
