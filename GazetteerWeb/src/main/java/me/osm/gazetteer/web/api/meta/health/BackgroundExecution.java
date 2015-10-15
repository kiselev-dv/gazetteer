package me.osm.gazetteer.web.api.meta.health;

import java.util.Collection;
import java.util.List;

public class BackgroundExecution {
	
	private int threads;

	private Collection<Integer> done;
	private Collection<Integer> queued;
	private Collection<Integer> active;
	
	public int getThreads() {
		return threads;
	}
	
	public void setThreads(int threads) {
		this.threads = threads;
	}
	
	public Collection<Integer> getDone() {
		return done;
	}
	
	public void setDone(Collection<Integer> done) {
		this.done = done;
	}
	
	public Collection<Integer> getQueued() {
		return queued;
	}
	
	public void setQueued(Collection<Integer> queued) {
		this.queued = queued;
	}
	
	public Collection<Integer> getActive() {
		return active;
	}

	public void setActive(Collection<Integer> active) {
		this.active = active;
	}
	
}
