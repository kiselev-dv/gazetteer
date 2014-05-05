package me.osm.gazetteer.web;

import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.elasticsearch.client.Client;
import org.elasticsearch.node.Node;

/**
 * Application Lifecycle Listener implementation class ESNodeHodel
 *
 */
@WebListener
public final class ESNodeHodel implements ServletContextListener {

	private static final ESNodeHodel INSTANCE = new ESNodeHodel();
	private Node node;
	
    /**
     * Default constructor. 
     */
    public ESNodeHodel() {
    	
    }
    
    public static Client getClient() {
    	return INSTANCE.node.client();
    }

	@Override
	public void contextDestroyed(ServletContextEvent arg0) {
		INSTANCE.stopNode();
	}


	@Override
	public void contextInitialized(ServletContextEvent arg0) {
		INSTANCE.createNode();
	}

	private void createNode() {
		this.node = nodeBuilder().clusterName("OSM-Gazetteer").node();
	}

	private void stopNode() {
		if(!this.node.isClosed()) {
			this.node.close();
		}
	}
	
}
