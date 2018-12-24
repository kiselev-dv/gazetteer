package me.osm.gazetteer.join.out_handlers;

import java.util.List;

import org.json.JSONObject;

/**
 * Base interface for out handlers
 *
 * You could process objects and
 * write addresses in different ways in one run.
 * Handlers provided through command line arguments.
 *
 * @author dkiselev
 */
public interface JoinOutHandler {

	/**
	 * Parse user inputed options
	 *
	 * @param options user input from shell
	 * @return parsed options
	 * */
	public HandlerOptions parseHandlerOptions(List<String> options);

	/**
	 * Instanciate and initialize hadler
	 *
	 * @param options parsed options
	 * @return handler instance
	 * */
	public JoinOutHandler initialize(HandlerOptions options);

	/**
	 * Process feature
     *
	 * @param object parsed object from stripe
	 * @param stripe name of processed stripe
	 *
	 * WARNING: This method is not thread safe.
	 * If you store some data from this method
	 * to class fields, use synchronization
	 * */
	public void handle(JSONObject object, String stripe);

	/**
	 * All features from this stripe were processed
	 *
	 * @param stripe name of the stripe
	 * */
	public void stripeDone(String stripe);


	/**
	 * All stripes were processed
	 * */
	public void allDone();

}
