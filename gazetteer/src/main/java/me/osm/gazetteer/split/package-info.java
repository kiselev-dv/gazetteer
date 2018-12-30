
/**
 * Splits osm data file into nodes, ways, relations files
 *
 * Relations and ways in osm doesn't have geometry and coordinates
 * they only have a references to nodes.
 * (Relations have members which are other relations, ways and nodes)
 *
 * So after we have filtered out which relations and ways we need by tags
 * we should load nodes underneath.
 *
 * Data stored in linear way (as a list of xml nodes).
 * So now it's faster to split data into three
 * files neither read whole file for many times.
 *
 * Actual job is performed in
 * me.osm.gazetteer.split.Split
 *
 * Next slice phase: @see me.osm.gazetteer.striper
 * */
package me.osm.gazetteer.split;
