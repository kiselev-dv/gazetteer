#!/bin/bash

mkdir $1/gazetteer-web
rm -r $1/gazetteer-web/*

cp -r config $1/gazetteer-web/config
cp -r target/lib $1/gazetteer-web/lib
cp target/GazetteerWeb-*-SNAPSHOT.jar $1/gazetteer-web/gazetteer-web.jar

cd $1/gazetteer-web
tar -czvf $1/gazetteer-web.tgz ./
