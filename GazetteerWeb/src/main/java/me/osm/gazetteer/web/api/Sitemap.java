package me.osm.gazetteer.web.api;

import java.io.UnsupportedEncodingException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.osm.gazetteer.web.Configuration;
import me.osm.gazetteer.web.ESNodeHodel;
import me.osm.gazetteer.web.Main;
import me.osm.gazetteer.web.api.imp.SitemapRender;
import me.osm.gazetteer.web.api.imp.XMLSitemapRender;
import me.osm.gazetteer.web.imp.IndexHolder;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.count.CountResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.restexpress.Request;
import org.restexpress.Response;

public class Sitemap {
	private static final int pageSize = Main.config().getSiteMapMapgeSize();
	
	private static final Pattern p = Pattern.compile(".*sitemap([0-9]+)\\.xml(\\.gz)?");
	private Configuration config;
	
	public void read(Request req, Response res)	{
		
		try	{
			
			String path = req.getPath();
			
			if(path.endsWith ("sitemap_index.xml")) {
				XMLSitemapRender render = new XMLSitemapRender(config);

				renderIndex(render);
				
				ChannelBuffer body = ChannelBuffers.wrappedBuffer(render.toString().getBytes("UTF-8"));
				res.setBody(body);
				res.setContentType("text/xml");
			}
			
			else {
				Matcher matcher = p.matcher(path);
				if(matcher.matches()) {
					XMLSitemapRender render = new XMLSitemapRender(config);
					
					renderPage(Integer.valueOf(matcher.group(1)), render);
					
					ChannelBuffer body = ChannelBuffers.wrappedBuffer(render.toString().getBytes("UTF-8"));
					res.setBody(body);
					res.setContentType("text/xml");
				}
			}
			
		}
		catch (Exception e) {
			res.setException(e);
		} 
	}

	public static void renderPage(int page, SitemapRender render) throws UnsupportedEncodingException {

		Client client = ESNodeHodel.getClient();
		
		SearchRequestBuilder searchQ = client.prepareSearch("gazetteer")
				.setTypes(IndexHolder.LOCATION)
				.setNoFields()
				.setQuery(QueryBuilders.matchAllQuery())
				.setExplain(false);
		
		searchQ.setSize(pageSize);
		searchQ.setFrom(page * pageSize);
		
		SearchResponse searchResponse = searchQ.get();
		
		render.pageBegin();
		
		for(SearchHit hit : searchResponse.getHits().getHits()) {
			
			String id = hit.getId();
			if(StringUtils.startsWith(id, "adrpnt") || StringUtils.startsWith(id, "poipnt")) {
				id = StringUtils.substringBefore(id, "--");
			}
			
			render.feature(id);
		}
		
		render.pageEnd();
	}

	public static void renderIndex(SitemapRender render) throws UnsupportedEncodingException {
		Client client = ESNodeHodel.getClient();
		
		CountResponse countResponse = client.prepareCount("gazetteer").setQuery(QueryBuilders.matchAllQuery()).get();
		
		long count = countResponse.getCount();
		
		render.indexBegin();
		
		for(int i = 0; i <= count / pageSize; i++) {
			render.page(i);
		}
		
		render.indexEnd();
	}
	
}
