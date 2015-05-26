gazetteer
=========

Takes osm file and create json/csv output for every feature with crossreferences. 

With next goals in mind:
* Easy to deploy (keep number of external dependencyes as low as possible)
* Memory friendly (most parts can be easily rewrited, to use file indexes and save ram)
* Clusterization friendly (build whole process as a number of tasks, which can be performed in multi-thread/multi-node environment)

Use latest release
------------------

You could find binaries here https://github.com/kiselev-dv/gazetteer/releases/


Or compile with apache maven
------------------

    # Install kiselev-dv/osm-doc-java
    mvn compile install -f osm-doc-java/pom.xml
    
    # Install kiselev-dv/ExternalSorting
    # It was forked, so test will fails
    mvn -Dmaven.test.skip=true compile install -f ExternalSorting/pom.xml
    
    # Compile and assemble gazetteer
    mvn clean compile assembly:single -f Gazetteer/pom.xml
    
  
how it works
------------

Data processed in two steps:

Stripe (map)
Join   (reduce)

Also there is a zero step split which splits single osm file into nodes, ways and relations.

See me.osm.gazetter package annotation for more info.

Typical workflow
----------------

	#Do some preparations: split whole OSM file into 3 files with nodes, ways, and relations.
    java -jar gazetteer.jar split country.osm
    
    #Parse data and stripe it
    java -jar gazetteer.jar slice
    
	#Do spatial join
	java -jar gazetteer.jar join --handlers out-gazetteer /path/to/save/out.json.gz
	

Results
-------

Each line of out.json.gz contains one object encoded as JSON object. 

Objects are
* poipnt POI (Places of interest)
* adrpnt Addresses (Building or node with addr:nousenumber)
* hghway Highway (Street or street segment. Each osm way will creates one or more hghway object)
* hghnet Highways networks (Streets grouped by names and upward boundaries)
* plcpnt Place point (Places)
* admbnd Admin boundary (Administrative boundaries)

Addresses stored inside address.parts array and arranged by levels as
* admin0_name
* admin1_name
* admin2_name
* local_admin_name
* locality_name
* street_name

See: /GazetteerWeb/src/main/resources/mappings/location.json

Javadoc
-------

https://kiselev-dv.github.io/gazetteer

Tips and Trics
--------------

#### 1 Speed and memory consumption

##### 1.a Speedup split

	# it's faster to use systems bunzip:
	bzcat country.osm.bz2 | java -jar gazetteer.jar split - none

##### 1.b Memory tweaking
	
	# First option is to use --x10 flag for slice
	# it says to gazetteer to slice data into smaller peaces  
	java -jar gazetteer.jar slice --x10

	# Second option is to split data step by step
	# See java -jar gazetteer.jar slice --help for list of types
	java -jar gazetteer.jar slice boundaries
	java -jar gazetteer.jar slice highways
	java -jar gazetteer.jar slice addresses
	
	# and then join as always
	java -jar gazetteer.jar join --handlers out-gazetteer file.json.gz
	
	# if you still gets OutOfMemory on join stage, try to set down number of threads
	java -jar gazetteer.jar --threads 2 join --handlers out-gazetteer file.json.gz 
	
#### 2 How to get only smth. (Streets only or POI's only and so on).
	    
	# Split as always 
	java -jar gazetteer.jar split country.osm
	
	# Specify type of data which will be used in slice stage
	java -jar gazetteer.jar slice boundaries places highways
	
	# Join as always
	java -jar gazetteer.jar join --handlers out-gazetteer file.json.gz
	   
#### 3 How to filter data by boundary
	     	
	# Use --check-boundaries boundary1 boundary2 ... Boundaries are combined via and. 
	# So result should have boundary1 and boundary2 and so on.
	# Boundaries encoded as [r,w]NNNNNNN 
	# r for relations, w for ways
	# NNNNNNN - relation or way number
	
	java -jar gazetteer.jar join --check-boundaries r123456 --handlers out-gazetteer file.json.gz
	
	# And remember, if you use --check-boundaries and boundary wasn't parsed during slice stage
	# results will be empty
	    
#### 4 What can I do with broken boundaries (see option 3).    
	    
	# Use --boundaries-fallback-file option.
	# Fall back file is a simple csv file with 
	# id, timestamp and geometry (as wkt) of boundaries
		
	# So you could add prebuilded boundary into this file
	# or use this file for every conversion, in this case
	# if new version of osm dump has broken boundary multipolygon
	# old version will be used. And if boundary successfully parsed
	# and has valid geometry, boundary fallback file will be updated. 
		
	java -jar gazetteer.jar slice --boundaries-fallback-file boundaries.csv
		
#### 5 POI classification.
	    
	# By default poi parsed and filtered according to https://github.com/kiselev-dv/osm-doc 
	# You could specify your own osm-doc xml via --poi-catalog
	
	java -jar gazetteer.jar slice --poi-catalog osm-doc.xml
	
