package me.osm.gazetteer.web.api.meta.health;

import java.util.Collection;

import me.osm.gazetteer.web.executions.BackgroudTaskDescription;

public class BackgroundExecution {
	
	private int threads;

	private Collection<BackgroudTaskDescription> done;
	private Collection<BackgroudTaskDescription> queued;
	private Collection<BackgroudTaskDescription> active;
	
	public int getThreads() {
		return threads;
	}
	
	public void setThreads(int threads) {
		this.threads = threads;
	}

	public Collection<BackgroudTaskDescription> getDone() {
		return done;
	}

	public void setDone(Collection<BackgroudTaskDescription> done) {
		this.done = done;
	}

	public Collection<BackgroudTaskDescription> getQueued() {
		return queued;
	}

	public void setQueued(Collection<BackgroudTaskDescription> queued) {
		this.queued = queued;
	}

	public Collection<BackgroudTaskDescription> getActive() {
		return active;
	}

	public void setActive(Collection<BackgroudTaskDescription> active) {
		this.active = active;
	}
	
}
