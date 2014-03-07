gazetteer
=========

Takes osm file and create html/json output for every feature with crossreferences. 

With next goals in mind:
* Easy to deploy (keep number of external dependencyes as low as possible)
* Memory friendly (most parts can be easily rewrited, to use file indexes and save ram)
* Clusterization friendly (build whole process as a number of tasks, which can be performed in multi-thread/multi-node environment)


compile with apache maven
------------------

    mvn clean compile assembly:single
  

usage
-----

    java -jar gazetter.jar slice [--slices-dir SLICES_DIR=slices] input-file.osm
    java -jar gazetter.jar join [--slices-dir SLICES_DIR=slices] 
    java -jar gazetter.jar out [--slices-dir SLICES_DIR=slices] {--out-json | --out-csv }
  
