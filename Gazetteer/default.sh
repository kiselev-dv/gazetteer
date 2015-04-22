#!/bin/bash

[[ -z "$1" ]] && exit 0

CC=$1
BASE=/home/dkiselev
LOG=$BASE/logs/gazetteer.log
JAR=$BASE/bin/gazetteer.jar

bzcat $BASE/$CC.osm.bz2 | java -jar $JAR split - none &>> $LOG

java -Xmx10g -jar $JAR --log-prefix $CC slice --boundaries-fallback-file $BASE/boundaries/$CC.csv &>> $LOG

java -Xmx10g -jar $JAR --log-prefix $CC join --find-langs --handlers out-gazetteer $BASE/out/$CC.json.gz &>> $LOG

