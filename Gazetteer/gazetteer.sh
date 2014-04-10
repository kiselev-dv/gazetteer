java -Xmx4g -jar gazetteer.jar --data-dir /opt/osm/data out-csv --columns id osm-id osm-type type addr-text addr-long-text postcode letter hn street [place:quarter place:neighbourhood nearest:neighbourhood] [place:village place:hamlet place:town place:city boundary:8] boundary:4 boundary:2 centroid full-geometry --types address street place

java -Xmx4g -jar gazetteer.jar --data-dir /opt/osm/data out-csv --columns id osm-id osm-type type [name:ru name] poi-class poi-class:ru more-tags.hstore more-tags.hstore:ru operator opening_hours brand phone fax website email addr-text addr-long-text postcode letter hn street [place:quarter place:neighbourhood nearest:neighbourhood] [place:village place:hamlet place:town place:city boundary:8] boundary:4 boundary:2 centroid --types poi

