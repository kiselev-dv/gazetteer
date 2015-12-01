package me.osm.gazetteer.web.executions;

import java.util.Map;

import me.osm.gazetteer.web.api.meta.health.Health;
import me.osm.gazetteer.web.utils.LocalDateTimeSerializer;

import org.elasticsearch.common.joda.time.LocalDateTime;
import org.elasticsearch.common.joda.time.Period;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

public class BackgroudTaskDescription {
	
	private int id;
	private String uuid;
	
	@JsonSerialize(using=LocalDateTimeSerializer.class)
	private LocalDateTime submitTs;
	@JsonSerialize(using=LocalDateTimeSerializer.class)
	private LocalDateTime runTs;
	@JsonSerialize(using=LocalDateTimeSerializer.class)
	private LocalDateTime doneTs;
	
	private String executionTime;
	private String waitTime;
	
	private String className;
	private Map<String, Object> parameters;
	
	public String getClassName() {
		return className;
	}
	public void setClassName(String className) {
		this.className = className;
	}
	public Map<String, Object> getParameters() {
		return parameters;
	}
	public void setParameters(Map<String, Object> parameters) {
		this.parameters = parameters;
	}
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	public LocalDateTime getSubmitTs() {
		return submitTs;
	}
	public void setSubmitTs(LocalDateTime submitTs) {
		this.submitTs = submitTs;
	}
	public LocalDateTime getRunTs() {
		return runTs;
	}
	public void setRunTs(LocalDateTime runTs) {
		this.runTs = runTs;
		if(this.submitTs != null) {
			this.waitTime = new Period(this.submitTs, this.runTs)
				.toString(Health.PERIOD_FORMATTER);
		}
	}
	public LocalDateTime getDoneTs() {
		return doneTs;
	}
	public void setDoneTs(LocalDateTime doneTs) {
		this.doneTs = doneTs;
		if(this.runTs != null) {
			this.executionTime = new Period(this.runTs, this.doneTs)
				.toString(Health.PERIOD_FORMATTER);
		}
	}
	public String getExecutionTime() {
		return executionTime;
	}
	public void setExecutionTime(String executionTime) {
		this.executionTime = executionTime;
	}
	public String getWaitTime() {
		return waitTime;
	}
	public void setWaitTime(String waitTime) {
		this.waitTime = waitTime;
	}
	public String getUuid() {
		return uuid;
	}
	public void setUuid(String uuid) {
		this.uuid = uuid;
	}
}
