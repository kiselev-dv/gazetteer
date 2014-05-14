#!/bin/bash

rm /opt/osm/data/*

java -Dfile.encoding=UTF8 -Xmx4g -jar gazetteer.jar --data-dir /opt/osm/data split $1

java -Dfile.encoding=UTF8 -Xmx6g -jar gazetteer.jar --data-dir /opt/osm/data slice all

java -Dfile.encoding=UTF8 -Xmx6g -jar gazetteer.jar \
--find-langs \
--data-dir /opt/osm/data join 



