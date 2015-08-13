GazetteerWeb
============

Lightweigth REST geocoding api.

Live demo http://osm.me

Supports:
* geocode requests
* reverse geocode (coordinates to address)
* suggestions
 
Project based on RestExpress and Elasticsearch and supports clusterization

Dependencies
------------

You'll need:
  
* https://github.com/kiselev-dv/ExternalSorting
* https://github.com/kiselev-dv/osm-doc-java
* https://github.com/kiselev-dv/osm-doc

Compile and run
---------------

Build dependencies

    mvn install -f ExternalSorting/pom.xml
    mvn install -f osm-doc-java/pom.xml

Build GazetteerWeb:

    mvn package -f gazetteer/GazetteerWeb/pom.xml

Maven will create a GazetteerWeb.tar.gz in target subdir. 
Or you could take it from github releases section. 
Unpac archive.  
 
 
Copy to working dir

    cp -r gazetteer/GazetteerWeb/lib $GazetteerWebHome/lib
    cp gazetteer/GazetteerWeb/target/GazetteerWeb.jar $GazetteerWebHome/gazetteer-web.jar
    cp -r gazetteer/GazetteerWeb/config $GazetteerWebHome/config
  
Run
  
    java -jar $GazetteerWebHome/gazetteer-web.jar


Usage and configuring
---------------------

Open `http://localhost:8080/api/info.json`

If you got 404 error, check `$GazetteerWebHome/config/dev/environment.properties`

Full URL looks like: `http://localhost:${port}${web_root}/info.json`

Edit `$GazetteerWebHome/config/dev/environment.properties` 

Set `admin_password_sha1` for password and 
`poi_catalog_path` path to `osm-doc`. 

You may get it from 
https://github.com/kiselev-dv/osm-doc

Import
------

Run `http://localhost:${port}${web_root}/location/_import?source=/path/to/Country.json.gz&drop=true&osmdoc=true`

* **source** path to Gazetteer addresses dump
* **drop** drop index before import
* **osmdoc** import osmdoc
 
    login: admin
    password: use password for admin_password_sha1 hash
    