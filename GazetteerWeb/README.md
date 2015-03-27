GazetteerWeb
============

Lightweigth REST geocoding api.

Live demo http://tr1.nowtaxi.ru/gazetteer/#!/ru/

Supports:
* geocode requests: /feature/_search?q=Moscow Tverskaya st. 1
* reverse geocode (coordinates to address) /_inverse?lat=45.0,lon=18.0
 
(Suggestions is on the way.)

Project based on RestExpress and Elasticsearch and supports clusterization

compile and run
-------
```
  #build
  mvn package
  
  #Copy to working dir
  cp -r target/lib $GAZETTEER_HOME/lib
  cp target/GazetteerWeb-version.jar $GAZETTEER_HOME/gazetteer-web.jar
  cp -r config $GAZETTEER_HOME/config
  
  #run
  java -jar $GAZETTEER_HOME/gazetteer-web.jar

```
