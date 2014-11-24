package me.osm.gazetter.join;

import java.io.File;
import java.util.List;
import java.util.Set;

import me.osm.gazetter.sortupdate.SortAndUpdateTask;

import org.json.JSONObject;

public class JoinAndUpdateTask implements Runnable {
	
	private JoinSliceRunable joinTask;
	private SortAndUpdateTask updateTask;

	public JoinAndUpdateTask(AddrJointHandler addrPointFormatter, File stripeF,
			List<JSONObject> common, Set<String> filter, JoinExecutor joiner) {
		
		joinTask = new JoinSliceRunable(addrPointFormatter, stripeF, common, filter, joiner, null);
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
