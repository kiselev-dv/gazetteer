#!/bin/sh

# If we have comand line args - treat first as config file
if [ ! -z "$1" ]; then
	echo "get config from $1"
	source "$1"
fi

# cd to jenkins/hudson workspace if it's set 
if [ ! -z "$WORKSPACE" ]; then
	cd $WORKSPACE
fi

# Basic environment setup
if [ -z "$BASE" ]; then
	BASE=$($HOME)
fi
if [ -z "$JAR" ]; then
	JAR=$($HOME/bin/gazetteer.jar)
fi

# CC - country code, if we have $country - set it 
if [ ! -z "$country" ]; then
	CC=$country
fi

# if we have dumpsrc - download 
if [ -n "$dumpsrc" ]; then
    echo "download $CC from $ $dumpsrc"
    wget --no-verbose -O $BASE/dumps/$CC.osm.bz2 $dumpsrc
fi

echo "`date` START Convert $CC (Boundary: $boundary)"
echo "Gazetteer version: "
java -jar $JAR --version

if [ -z "$dump" ]; then
	dump="$BASE/dumps/$CC.osm.bz2"
fi

if [ -z $nosplit ] || [ $nosplit = false ]; then
    echo "`date` Split $CC, Dump: $dump"
    bzcat $dump | java -jar $JAR split - none
else
    echo "Skip split"
fi

M="-Xmx"$mem"g"
if [ "default" = "$mem" ] || [ -z $mem ]; then
	M="-Xmx10g"
fi

bcc=$(grep "$boundary" $BASE/boundaries/$CC.csv | wc -l)
bca=$(grep "$boundary" $BASE/boundaries/Countries.csv | wc -l)
if [ ! -z $boundary ] && [ $bcc -lt 1 ] && [ -f $BASE/boundaries/Countries.csv ] && [ $bca -gt 0 ]; then
    grep "$boundary" $BASE/boundaries/Countries.csv >> $BASE/boundaries/$CC.csv
    echo "Boundary $boundary taken from $BASE/boundaries/Countries.csv"
fi

echo "`date` Slice $CC (x10: $x10)"
X=""
if [ "$x10" = true ]; then
    X="--x10"
fi

java $M -jar $JAR --log-prefix $CC slice $X --boundaries-fallback-file $BASE/boundaries/$CC.csv

if [ ! -z $boundary ] && [ $(grep "$boundary" $BASE/boundaries/$CC.csv | wc -l) -lt 1 ]; then
    echo "Boundary $boundary hasn't builded. Abort Join"
    cp -f $BASE/bak/$CC.json.gz $BASE/out/$CC.json.gz1
    exit 2
fi

if [ "$dbg" = true ]; then
    cp -f data/binx.gjson.gz $BASE/deploy/debug/$CC.binx.json.gz
fi

echo "`date` Join $CC (Threads: $threads Boundary: $boundary) "

T="--threads $threads"
if [ "$threads" = "auto" ] || [ -z $threads ]; then
    T=""
fi

B="--check-boundaries $boundary"
if [ -z $boundary ]; then
    B=""
fi

H="out-gazetteer out=$CC.json.gz poi_catalog=$BASE/osm-doc/catalog/"
if [ ! -z "$csv" ]; then
	hdr=""
	if [ ! -z "$csvhdr" ]; then
		hdr="header=$csvhdr"
	fi
    H="out-csv types=adrpnt out=$CC.csv columns=$csv $hdr"
fi
java $M -jar $JAR --log-prefix $CC $T join --find-langs --clean-stripes $B --handlers $H


if [ ! -f "$CC.json.gz" ] && [ ! -f "$CC.csv" ]; then
  echo "$CC.json.gz not found"
  echo "`date` FAIL Convert $CC"
  exit 1
fi

if [ ! -f "$CC.csv" ]; then
    SIZE=$(stat -c '%s' $CC.json.gz)
    if [ $SIZE -lt 1024 ]; then
        echo "$CC.json.gz is too small. Size: $SIZE kb"
        echo "`date` FAIL Convert $CC"
        exit 1
    fi
fi

if [ -f "$CC.csv" ]; then
    echo "`date` move results to $BASE/out/$CC.csv"
    mv -f "$CC.csv" "$BASE/out/$CC.csv"
fi

if [ -f "$CC.json.gz" ]; then

    if [ "$diff" = true ]; then
        echo "`date` Calculate diff"
        DDF=$(date +"%Y%m%d")
        java -Xmx4g -jar $JAR diff --out-file $BASE/out/diff/$CC.$DDF.diff --old $BASE/out/$CC.json.gz --new $CC.json.gz
        echo "`date` compress diff"
        gzip -f $BASE/out/diff/$CC.$DDF.diff
    fi

    echo "`date` move results to $BASE/out/$CC.json.gz"
    mv -f "$CC.json.gz" "$BASE/out/$CC.json.gz"
fi

if [ "$cleardump" = true ]
then
    echo "Remove osm dump"
    rm $BASE/dumps/$CC.osm.bz2
fi

echo "`date` DONE Convert $CC"

exit 0