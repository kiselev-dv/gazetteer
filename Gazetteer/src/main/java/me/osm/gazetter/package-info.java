/**
 * Converts osm data file into json suitable for geocoding.
 * 
 * Data processed in three steps:
 * 
 * I Split
 * ------- 
 * 
 * First, split osm xml file into three files with nodes ways and relations
 * @see me.osm.gazetter.split
 *       
 * II Slice 
 * --------
 * 
 * 1. Read Relations, Ways and Nodes.
 * 2. Filter them by tags
 * 3. Build geometry (find nodes for relations and ways)
 * 4. Slice geometry (boundaries and ways) into stripes. 
 *    So join phase may be performed in a parallel way.
 * 
 * @see me.osm.gazetter.striper   
 *
 * III Join
 * --------
 * 
 * At this phase we perform point in polygon location
 * to find all boundaries for addr. node or way.
 * 
 * Also at this step we look for associated streets, 
 * and addr:city locations.
 * 
 * After all parts of address was melted into one JSONObject
 * it passes into out handlers. Out handlers adapt object for
 * users purposes. They may write data as csv or JSON or 
 * do whatever you want.
 * 
 * @see me.osm.gazetter.join
 *        
 * See also
 * --------
 *        
 * There are also some util and experimental packages.
 * @see me.osm.gazetter.Gazetteer - it's entry point for executable jar
 * @see me.osm.gazetter.Options - it's configuration holder
 * 
 * N.b. There are no single run solution yet. It was maid for purpose.
 * Each step takes different time, memory and generates different io loading.
 * So you could manage RAM with -Xmx more genuinely.
 * 
 * For instance Split may be performed with -Xmx16m 
 * (it does't actually stores anything in RAM)
 * And Join stage would take -Xmx16g especially if you
 * have a lots of threads.       
 * */
package me.osm.gazetter;