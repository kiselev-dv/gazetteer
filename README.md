OpenStreetMap (OSM) geocoder
======================

Main purpose of this project is easy to use geocoder/geoindexer.

Project consists of two parts: Gazetteer and GazetteerWeb

Gazetteer
=========

Gazetteer used to parse *osm* data and do all dirty work with geometry.

You can use Gazetteer as standalone *osm* processor, to dump addresses from *osm*.

You can ignore GazetteerWeb and use data in your own geocoding/geosearching applications.
Take an osm.bz2 dump and generate `json` with

* full geocoded buildins
* full geocoded POIs
* streets
* cyties
* administrative boundaries

Details are here https://github.com/kiselev-dv/gazetteer/tree/develop/Gazetteer

You could find data extracts here: http://data.osm.me/dumps/

GazetteerWeb
============

GazetteerWeb is a second part of the project. 
You may take it as example implementation of search engine for Gazetteer generated data or use it for your own purposes.

Details are here https://github.com/kiselev-dv/gazetteer/tree/develop/GazetteerWeb

Live demo map: http://osm.me/
(covers Russia, Montenegro, Croatia, Bosnia and Hercegovina)
