package me.osm.gazetteer.web;

import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

import org.elasticsearch.client.Client;
import org.elasticsearch.node.Node;

public final class ESNodeHodel {

	private static final Node node = nodeBuilder().clusterName("OSM-Gazetteer").node();
	
    private ESNodeHodel() {
    	
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
