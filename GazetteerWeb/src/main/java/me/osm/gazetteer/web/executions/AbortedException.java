package me.osm.gazetteer.web.executions;

public class AbortedException extends Exception {
	
	private static final long serialVersionUID = 4947214309158106921L;
	private boolean byUser;
	
	public AbortedException(String message, Throwable cause, boolean normal) {
		super(message, cause);
		
		this.byUser = normal;
	}
	
	public boolean isByUser() {
		return byUser;
	}

}
