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

usage: gazetter [-h] [--data-dir DATA_DIR] [--log-level LOG_LEVEL]
                {split,slice,join,out} ...

Create alphabetical index of osm file features

positional arguments:
  {split,slice,join,out}
    split                Prepare osm data. Split nodes, ways and relations.
    slice                Parse features from  osm  data  and  write it into
                         stripes 0.1 degree wide.
    join                 Join features.  Made  spatial  joins  for  address
                         points inside polygons and so on.
    out                  Write data out in different formats.

optional arguments:
  -h, --help             show this help message and exit
  --data-dir DATA_DIR    Use folder as data storage. (default: slices)
  --log-level LOG_LEVEL  (default: WARN)
  
