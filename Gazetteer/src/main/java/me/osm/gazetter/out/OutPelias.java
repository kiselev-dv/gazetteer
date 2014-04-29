package me.osm.gazetter.out;

import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

import java.io.File;

import me.osm.gazetter.join.Joiner;
import me.osm.gazetter.striper.FeatureTypes;
import me.osm.gazetter.utils.FileUtils;
import me.osm.gazetter.utils.FileUtils.LineHandler;

import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.client.AdminClient;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.Node;
import org.json.JSONObject;


public class OutPelias implements LineHandler {
	
	private String stripeFolder;
	private File folder;
	
	public OutPelias(String stripeFolder) {
		this.stripeFolder = stripeFolder;
		this.folder = new File(this.stripeFolder);
	}

	public void createIndex() {
		
		Node node = nodeBuilder().node();
		Client client = node.client();
		
		AdminClient admin = client.admin();
		
		Settings settings = ImmutableSettings.builder().loadFromClasspath("pelias_schema.json").build();
		
		admin.indices().create(new CreateIndexRequest("pelias", settings)).actionGet();
		
		node.close();
	}
	
	public void go() {
		for(File stripeF : folder.listFiles(Joiner.STRIPE_FILE_FN_FILTER)) {
			FileUtils.handleLines(stripeF, this);
		}
	}

	@Override
	public void handle(String s) {
		JSONObject feature = new JSONObject(s);
		if(FeatureTypes.ADDR_POINT_FTYPE.equals(feature.getString("ftype"))) {
			
		}
	}
	
}
