package me.osm.gazetteer.web;

import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.Node;

public final class ESNodeHolder {

	private static final Node node = nodeBuilder().settings(
			Settings.builder()
	        	.put("path.home", GazetteerWeb.config().getESHome()))
			.clusterName("OSM-Gazetteer").node();
	
    private ESNodeHolder() {
    	
    }
    
    public static Client getClient() {
    	return node.client();
    }

	public static void stopNode() {
		if(!node.isClosed()) {
			node.close();
		}
	}
	
}
