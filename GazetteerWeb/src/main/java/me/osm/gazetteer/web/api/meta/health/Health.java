package me.osm.gazetteer.web.api.meta.health;

import java.util.Map;

import org.elasticsearch.common.joda.time.Period;
import org.elasticsearch.common.joda.time.format.PeriodFormatter;
import org.elasticsearch.common.joda.time.format.PeriodFormatterBuilder;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonGetter;

public class Health {

	private long uptime;
	
	private long freeMemMB;
	private long maxMemMB;
	
	private long features;
	private Map<String, Long> counters;
	
	private String esnodeError;
	
	private BackgroundExecution backgroundTasks;

	public long getUptime() {
		return uptime;
	}

	public void setUptime(long uptime) {
		this.uptime = uptime;
	}

	@JsonGetter
	public String getUptimeHR() {
		return new Period(uptime).toString();
	}

	public long getFreeMemMB() {
		return freeMemMB;
	}

	public void setFreeMemMB(long freeMemMB) {
		this.freeMemMB = freeMemMB;
	}

	public long getMaxMemMB() {
		return maxMemMB;
	}

	public void setMaxMemMB(long maxMemMB) {
		this.maxMemMB = maxMemMB;
	}

	public long getFeatures() {
		return features;
	}

	public void setFeatures(long features) {
		this.features = features;
	}

	public Map<String, Long> getCounters() {
		return counters;
	}

	public void setCounters(Map<String, Long> counters) {
		this.counters = counters;
	}

	public BackgroundExecution getBackgroundTasks() {
		return backgroundTasks;
	}

	public void setBackgroundTasks(BackgroundExecution backgroundTasks) {
		this.backgroundTasks = backgroundTasks;
	}

	public String getEsnodeError() {
		return esnodeError;
	}

	public void setEsnodeError(String esnodeError) {
		this.esnodeError = esnodeError;
	}
	
}
