#!/bin/bash

rm /opt/osm/data/*

java -Dfile.encoding=UTF8 -Xmx4g -jar gazetteer.jar --data-dir /opt/osm/data split /opt/osm/RU.osm.bz2

java -Dfile.encoding=UTF8 -Xmx6g -jar gazetteer.jar --data-dir /opt/osm/data slice all

java -Dfile.encoding=UTF8 -Xmx6g -jar gazetteer.jar \
--threads 3 \
--data-dir /opt/osm/data join \
--skip-in-text boundary:8 boundary:3 \
--check-boundaries r60189 \
--addr-parser /opt/osm/gazetteer/Gazetteer/ScriptsExamples/ruAddressesParser.groovy \
--addr-order CITY_STREET_HN

java -Dfile.encoding=UTF8 -Xmx4g -jar gazetteer.jar --data-dir /opt/osm/data out-csv --columns \
uid \
osm-type-id \
type \
type-verbose \
addr-text \
postcode \
[hn name:ru name] \
hn \
street \
street.uid \
[place:village place:hamlet] \
[place:village.id place:hamlet.id] \
[place:town place:city] \
[place:town.id place:city.id] \
boundary:6 \
boundary:6.id \
boundary:4 \
boundary:4.id \
boundary:2 \
boundary:2.id \
centroid \
[full-geometry centroid] \
--types address street place boundaries \
--line-handler /opt/osm/gazetteer/Gazetteer/ScriptsExamples/osmruCSVHandler.groovy \
--out-file /opt/osm/ru-addr.csv

java -Dfile.encoding=UTF8 -Xmx4g -jar gazetteer.jar --data-dir /opt/osm/data out-csv --columns \
id \
osm-type-id \
type \
[name:ru name] \
poi-class \
poi-class:ru \
more-tags.hstore \
more-tags.hstore:ru \
operator \
opening_hours \
brand \
phone \
fax \
website \
email \
addr-text \
addr-long-text \
postcode \
hn \
street \
[place:village place:hamlet] \
[place:village.id place:hamlet.id] \
[place:town place:city] \
[place:town.id place:city.id] \
boundary:4 \
boundary:4.id \
boundary:2 \
boundary:2.id \
centroid \
--types poi \
--line-handler /opt/osm/gazetteer/Gazetteer/ScriptsExamples/osmruPoiCsvFilter.groovy \
--out-file /opt/osm/ru-poi.csv


