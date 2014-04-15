java -Xmx4g -jar gazetteer.jar --data-dir /opt/osm/data split /opt/osm/ru.osm.bz2

java -Xmx6g -jar gazetteer.jar --data-dir /opt/osm/data slice all

java -Xmx6g -jar gazetteer.jar --data-dir /opt/osm/data join \
--skip-in-text boundary:8 boundary:3 \
--check-boundaries r60189 \
--addr-parser /home/dkiselev/osm/osm-gazetter/Gazetteer/ScriptsExamples/ruAddressesParser.groovy \
--addr-order CITY_STREET_HN

java -Xmx4g -jar gazetteer.jar --data-dir /opt/osm/data out-csv --columns \
id \
osm-type-id \
type \
type-verbose \
addr-text \
postcode \
hn \
[name:ru name] \
street \
street.id \
[place:village place:hamlet] \
[place:village.id place:hamlet.id] \
[place:town place:city] \
[place:town.id place:city.id] \
boundary:4 \
boundary:4.id \
boundary:2 \
boundary:2.id \
centroid \
full-geometry \
--types address street place \
--line-handler /home/dkiselev/osm/osm-gazetter/Gazetteer/ScriptsExamples/osmruCSVHandler.groovy \
--out-file /opt/osm/ru-kgd-addr.csv

java -Xmx4g -jar gazetteer.jar --data-dir /opt/osm/data out-csv --columns \
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
--types poi 


