package me.osm.gazetteer.web.api.meta.health;

import me.osm.gazetteer.web.executions.BackgroudTaskDescription;

public class AbortedTaskError {
	
	private BackgroudTaskDescription task;
	private String error;
	
	public AbortedTaskError(BackgroudTaskDescription task, String error) {
		super();
		this.task = task;
		this.error = error;
	}

	public BackgroudTaskDescription getTask() {
		return task;
	}
	
	public void setTask(BackgroudTaskDescription task) {
		this.task = task;
	}
	
	public String getError() {
		return error;
	}
	
	public void setError(String error) {
		this.error = error;
	}

}
