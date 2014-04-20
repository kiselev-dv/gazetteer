package me.osm.gazetter.join;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.index.quadtree.Quadtree;

public class JoinBoundariesTask implements Runnable {

	private BoundaryCortage up;
	private Quadtree index;
	private Map<BoundaryCortage, BoundaryCortage> bhierarchy;

	public JoinBoundariesTask(BoundaryCortage up, Quadtree qt,
			Map<BoundaryCortage, BoundaryCortage> bhierarchy) {
		
		this.up = up;
		this.index = qt;
		this.bhierarchy = bhierarchy;
	}

	@Override
	public void run() {
		
		Envelope env = up.getGeometry().getEnvelopeInternal();
		List<BoundaryCortage> downs = index.query(env);
		
		for(BoundaryCortage bc : downs) {
			if(up.getGeometry().covers(bc.getGeometry())) {
				savePair(up, bc);
			}
		}
		
	}

	private void savePair(BoundaryCortage up, BoundaryCortage down) {
		BoundaryCortage ref = down.copyRef();
		this.bhierarchy.put(ref, up.copyRef());
	}

}
