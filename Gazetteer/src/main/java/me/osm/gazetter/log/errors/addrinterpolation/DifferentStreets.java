package me.osm.gazetter.log.errors.addrinterpolation;

import me.osm.gazetter.log.GazetteerLogMessage;
import me.osm.gazetter.log.LogLevel;
import me.osm.gazetter.log.LogLevel.Level;

import org.joda.time.LocalDateTime;
import org.slf4j.Logger;

public class DifferentStreets extends GazetteerLogMessage {

	private static final long serialVersionUID = 2041099727062380871L;

	@SuppressWarnings("unused")
	private static String error = DifferentStreets.class.getSimpleName();

	@SuppressWarnings("unused")
	private String timestamp = LocalDateTime.now().toString();
	
	private Long interpolation;

	private Long node;

	private String street1;

	private String street2;
	
	public DifferentStreets(Long interpolation, Long node, String street1, String street2) {
		this.interpolation = interpolation;
		this.node = node;
		this.street1 = street1;
		this.street2 = street2;
	}

	public Long getInterpolation() {
		return interpolation;
	}

	public Long getNode() {
		return node;
	}

	public String getStreet1() {
		return street1;
	}

	public String getStreet2() {
		return street2;
	}

	@Override
	public void log(Logger root, Level level) {
		
		LogLevel.log(root, level, 
				"Addr interpolation error: "
			  + "Different streets for interpolated addresses. "
			  + "Streets are: \"{}\" and \"{}\". "
			  + "Interpolation way is: {} "
			  + "Node: {}", street1, street2, interpolation, node );
	}
	
}
