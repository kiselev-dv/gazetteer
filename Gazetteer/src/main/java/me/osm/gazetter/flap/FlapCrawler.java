package me.osm.gazetter.flap;

import java.io.File;
import java.io.PrintWriter;
import java.net.URL;

import me.osm.gazetter.utils.FileUtils;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public class FlapCrawler {
	
	public static void main(String[] args) {
		FlapCrawler.crawl("5000056", "/opt/osm/5000056.flap.csv");
	}
	
	private static final String urlTemplate = 
			"http://api.flap.biz/search/object?cityId={city}&lang=ru&limit={limit}&offset={ofset}";

	public static void crawl(String city, String outFile) {
		int limit = 100;
		int offset = 0;
		long counter = 0;
		
		try {
			PrintWriter printwriter = FileUtils.getPrintWriter(new File(outFile), false);
			
			while (true) {
				String url = StringUtils.replace(urlTemplate, "{city}", city);
				url = StringUtils.replace(url, "{limit}", String.valueOf(limit));
				url = StringUtils.replace(url, "{ofset}", String.valueOf(offset));
				
				offset += limit;
	
					String resultString = StringUtils.join(IOUtils.readLines((new URL(url)).openStream()));
					JSONObject page = new JSONObject(resultString.substring(1, resultString.length() - 1));
					JSONArray objectsArray = page.getJSONArray("objects");
					long total = page.getLong("total");
					
					if(objectsArray.length() == 0 || counter >= total || counter >= 10000) {
						break;
					}
					
					for(int i=0; i<objectsArray.length();i++) {
						JSONObject obj = objectsArray.getJSONObject(i);
						
						StringBuilder row = new StringBuilder();
						row.append(obj.optString("id")).append("\t");
						row.append(obj.optString("title")).append("\t");
						row.append(obj.optString("latitude")).append("\t");
						row.append(obj.optString("longitude")).append("\t");
						row.append(counter);
						
						printwriter.println(row);
						counter++;
					}
					
					Thread.sleep(500);
				
				}
			printwriter.flush();
			printwriter.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
}
