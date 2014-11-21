package me.osm.gazetter.join;

import java.io.File;
import java.util.List;
import java.util.Set;

import me.osm.gazetter.sortupdate.SortAndUpdateTask;

import org.json.JSONObject;

public class JoinAndUpdateTask implements Runnable {
	
	private JoinSliceTask joinTask;
	private SortAndUpdateTask updateTask;

	public JoinAndUpdateTask(AddrJointHandler addrPointFormatter, File stripeF,
			List<JSONObject> common, Set<String> filter, Joiner joiner) {
		
		joinTask = new JoinSliceTask(addrPointFormatter, stripeF, common, filter, joiner, null);
		updateTask = new SortAndUpdateTask(stripeF);
	}

	@Override
	public void run() {
		joinTask.run();
		joinTask = null;
		System.gc();
		updateTask.run();
	}

}
