/**
 * Read, parse, filter, build geometry and slice it
 *
 * Overview
 * --------
 *
 * 1. Read Relations, Ways and Nodes.
 * 2. Filter them by tags
 * 3. Build geometry (find nodes for relations and ways)
 * 4. Slice geometry (boundaries and ways) into stripes.
 *    So join phase may be performed in a parallel way.
 *
 * Details
 * -------
 *
 * * Entry point of split process is Slicer.run
 *
 * * Slicer runs reading and filtering, and for
 *   red and filtered objects slices geometry into smaller peaces.
 *
 * * me.osm.gazetteer.striper.readers are responsible for xml stream parsing,
 *   they reads xml, create POJOs and calls
 *   PointsReader.PointsHandler,
 *   WaysReader.WaysHandler
 *   and RelationsReader.RelationsHandler accordingly.
 *
 *
 * * me.osm.gazetteer.striper.builders are responsible for filtering xml data
 *   parsed by me.osm.gazetteer.striper.readers and building features like
 *   boundaries highways etc. and their geometry.
 *
 * * After me.osm.gazetteer.striper.builders have built a feature it calls
 *   their me.osm.gazetteer.striper.builders.handlers and passes builded feature.
 *
 * */
package me.osm.gazetteer.striper;
