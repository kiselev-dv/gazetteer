gazetteer
=========

Takes osm file and create json/csv output for every feature with crossreferences. 

With next goals in mind:
* Easy to deploy (keep number of external dependencyes as low as possible)
* Memory friendly (most parts can be easily rewrited, to use file indexes and save ram)
* Clusterization friendly (build whole process as a number of tasks, which can be performed in multi-thread/multi-node environment)
 

compile with apache maven
------------------

    Install kiselev-dv/osm-doc-java first
    (mvn compile install -f osm-doc-java/pom.xml)
    
    mvn clean compile assembly:single -f Gazetteer/pom.xml
    
  
how it works
------------

Data processed in two steps:

First, parse OSM file, find addresses (objects with addr:housenumber), administrative boundaries, places of interest and so on. This stage takes a lots of memory to keep references from ways and relations to nodes, but you can save some RAM by splitting this task in independant subtasks. 

For example you can process only boundaries and highways as a first task, after that process addresses and POIs.

At this stage data will be sliced into stripes of 0.1 degree wide.

Second, spatialy join points and polygons in each stripe generated with step 1 independantly.

So tipical workflow looks like:

	#Do some preparations: split whole OSM file into 3 files with nodes, ways, and relations.
    java -jar gazetteer.jar split country.osm
    
    #Parse data and stripe it
    java -jar gazetteer.jar slice all
    
    #or you can split the task to save memory
    #java -jar gazetteer.jar slice boundaries places
    #java -jar gazetteer.jar slice highways
    #java -jar gazetteer.jar slice addresses pois

	#Do spatial join
	java -jar gazetteer.jar join
	
	#Write data in json
	java -jar gazetteer.jar out-gazetteer
	
	#or in csv
	java -jar gazetteer.jar out-csv --columns id, address

usage
-----

<pre>
usage: gazetter [-h] [--threads THREADS] [--no-compress [NO_COMPRESS]] [--data-dir DATA_DIR] [--log-level LOG_LEVEL] [--log-file LOG_FILE] {man,split,slice,join,synchronize,out-csv,out-gazetteer} ...

Create alphabetical index of osm file features

positional arguments:
  {man,split,slice,join,synchronize,out-csv,out-gazetteer}
    man                  Prints extended usage
    split                Prepare osm data. Split nodes, ways and relations.
    slice                Parse features from osm data and write it into stripes 0.1 degree wide.
    join                 Join features. Made spatial joins for address points inside polygons and so on.
    synchronize          Sort and update features. Remove outdated dublicates.
    out-csv              Write data out in csv format.
    out-gazetteer        Write data out in json format with gazetter/pelias format.

optional arguments:
  -h, --help             show this help message and exit
  --threads THREADS      set number of threads avaible
  --no-compress [NO_COMPRESS]
                         Do not cmpress tepmlorary stored data (default: true)
  --data-dir DATA_DIR    Use folder as data storage. (default: data)
  --log-level LOG_LEVEL  (default: WARN)
  --log-file LOG_FILE

Commands:

MAN

usage: gazetter man [-h]

optional arguments:
  -h, --help             show this help message and exit



SPLIT

usage: gazetter split [-h] osm_file

positional arguments:
  osm_file               Path to osm file. *.osm *.osm.bz *.osm.gz supported.

optional arguments:
  -h, --help             show this help message and exit



SLICE

usage: gazetter slice [-h] [--poi-catalog POI_CATALOG] [--excclude-poi-branch [EXCCLUDE_POI_BRANCH [EXCCLUDE_POI_BRANCH ...]]] [--named-poi-branch [NAMED_POI_BRANCH [NAMED_POI_BRANCH ...]]] [--drop [DROP [DROP ...]]]
                [{all,boundaries,places,highways,addresses,pois} [{all,boundaries,places,highways,addresses,pois} ...]]

positional arguments:
  {all,boundaries,places,highways,addresses,pois}
                         Parse and slice axact feature(s) type.

optional arguments:
  -h, --help             show this help message and exit
  --poi-catalog POI_CATALOG
                         Path to osm-doc catalog xml file. By default internal osm-doc.xml will be used.
  --excclude-poi-branch [EXCCLUDE_POI_BRANCH [EXCCLUDE_POI_BRANCH ...]]
                         Exclude branch of osm-doc features hierarchy. Eg: osm-ru:transport where osm-ru is a name of the hierarchy, and transport is a name of the branch
  --named-poi-branch [NAMED_POI_BRANCH [NAMED_POI_BRANCH ...]]
                         Kepp POIS from this banch only if they have name tag
  --drop [DROP [DROP ...]]
                         List of objects osm ids which will be dropped ex r60189.



JOIN

usage: gazetter join [-h] [--common COMMON] [--addr-order {HN_STREET_CITY,STREET_HN_CITY,CITY_STREET_HN}] [--addr-parser ADDR_PARSER] [--check-boundaries [CHECK_BOUNDARIES [CHECK_BOUNDARIES ...]]]
                [--skip-in-text [SKIP_IN_TEXT [SKIP_IN_TEXT ...]]] [--find-langs [FIND_LANGS]]

optional arguments:
  -h, --help             show this help message and exit
  --common COMMON        Path for *.json with array of features which will be added to boundaries list for every feature.
  --addr-order {HN_STREET_CITY,STREET_HN_CITY,CITY_STREET_HN}
                         How to sort addr levels in full addr text
  --addr-parser ADDR_PARSER
                         Path to *.groovy file with full addresses texts formatter.
  --check-boundaries [CHECK_BOUNDARIES [CHECK_BOUNDARIES ...]]
                         Filter only addresses inside any of boundary given as osm id. eg. r12345 w123456 
  --skip-in-text [SKIP_IN_TEXT [SKIP_IN_TEXT ...]]
                         Skip in addr full text.
  --find-langs [FIND_LANGS]
                         Search for translated address rows. 
                         Eg. if street and all upper addr levels 
                         have name name:uk name:ru name:en 
                         generate 4 address rows.
                         If one of [name:uk name:ru name:en] is equals 
                         to name still generate additional row. 
                         (You can filter it later with simple distinct check).



UPDATE

usage: gazetter synchronize [-h]

optional arguments:
  -h, --help             show this help message and exit



OUT CSV

usage: gazetter out-csv [-h] [--columns COLUMNS [COLUMNS ...]] [--types {address,street,place,poi,boundaries} [{address,street,place,poi,boundaries} ...]] [--out-file OUT_FILE] [--poi-catalog POI_CATALOG]
                [--line-handler LINE_HANDLER]

optional arguments:
  -h, --help             show this help message and exit
  --columns COLUMNS [COLUMNS ...]
  --types {address,street,place,poi,boundaries} [{address,street,place,poi,boundaries} ...]
  --out-file OUT_FILE
  --poi-catalog POI_CATALOG
                         Path to osm-doc catalog xml file. By default internal osm-doc.xml will be used.
  --line-handler LINE_HANDLER
                         Path to custom groovy line handler.



OUT GAZETTEER

usage: gazetter out-gazetteer [-h] [--out-file OUT_FILE] [--poi-catalog POI_CATALOG] [--local-admin LOCAL_ADMIN] [--locality LOCALITY] [--neighborhood NEIGHBORHOOD]

optional arguments:
  -h, --help             show this help message and exit
  --out-file OUT_FILE
  --poi-catalog POI_CATALOG
                         Path to osm-doc catalog xml file. By default internal osm-doc.xml will be used.
  --local-admin LOCAL_ADMIN
                         Addr levels for local administrations.
  --locality LOCALITY    Addr levels for locality.
  --neighborhood NEIGHBORHOOD
                         Addr levels for neighborhood.

</pre>


Results
-----------------------------------

Example of generated JSON. see https://github.com/kiselev-dv/gazetteer/blob/develop/GazetteerWeb/src/main/resources/gazetteer_schema.json for full schema notation.

```javascript
	
{
    "id": "adrpnt-1839827750-w162863755-regular",
    "timestamp": "2014-10-22T00:11:07.059Z",
    "addr_level": "housenumber",
    "address": "21, Орјенског Батаљона Orjenskog Bataljona, Herceg Novi, Општина Херцег-Нови, Crna Gora",
    "admin0_alternate_names": ["Montenegro", "Crna Gora"],
    "admin0_name": "Crna Gora",
    "center_point": {
        "lon": 18.53050815,
        "lat": 42.4561645
    },
    "feature_id": "adrpnt-1839827750-w162863755",
    "full_geometry": {
        "type": "polygon",
        "coordinates": [
            [
                [18.5305918, 42.4560824],
                [18.5306252, 42.4561411],
                [18.5306462, 42.456178],
                [18.5304245, 42.4562466],
                [18.5303701, 42.456151],
                [18.5305918, 42.4560824]
            ]
        ]
    },
    "housenumber": "21",
    "local_admin_alternate_names": ["Herceg-Novi municipality"],
    "local_admin_name": "Општина Херцег-Нови",
    "locality_name": "Herceg Novi",
    "md5": "e0d9d0ad86334a7ce0800483f6005362",
    "nearby_places": [{
        "id": "plcpnt-3536850913-n428757227",
        "alt_names": ["Нивице", "Њивице"],
        "name": "Njivice",
        "place": "village"
    }, {
        "id": "plcpnt-1759978530-n123858919",
        "alt_names": ["Херцег-Новий", "Херцег-Нови", "Herceg Novi", "Херцег Нови"],
        "name": "Herceg Novi",
        "place": "town"
    }, {
        "id": "plcpnt-4095942822-n1609034696",
        "alt_names": ["Musići", "Musici"],
        "name": "Musići",
        "place": "village"
    }, {
        "id": "plcpnt-2619445936-n1609034725",
        "alt_names": ["Tušupi", "Tusupi"],
        "name": "Tušupi",
        "place": "village"
    }, {
        "id": "plcpnt-3258578329-n1609034718",
        "alt_names": ["Sušići", "Susici", "Сушићи"],
        "name": "Sušići",
        "place": "hamlet"
    }],
    "nearby_streets": [{
        "id": "hghway-3335219380-w162127428",
        "highway": "residential",
        "name": "ulica Sveta Bubala"
    }, {
        "id": "hghway-1424522199-w162094657",
        "highway": "steps",
        "name": "stepenište Ive Andrića"
    }, {
        "id": "hghway-4231035552-w162094674",
        "highway": "service",
        "name": "stepenište Ive Andrića"
    }, {
        "id": "hghway-0204807266-w130291268",
        "highway": "tertiary",
        "alt_names": ["Njegoseva", "Његошева"],
        "name": "Njegoševa"
    }, {
        "id": "hghway-3379516457-w131095794",
        "highway": "residential",
        "name": "ulica Sveta Bubala"
    }, {
        "id": "hghway-4000856859-w145925692",
        "highway": "pedestrian",
        "alt_names": ["Šetalište 5 Danica", "Шеталиште 5 Даница"],
        "name": "Šetalište 5 Danica"
    }, {
        "id": "hghway-2842308655-w236080190",
        "highway": "residential",
        "name": "Orjenskog Bataljona"
    }, {
        "id": "hghway-2686937743-w206548094",
        "highway": "footway",
        "name": "Bajer"
    }, {
        "id": "hghway-2880677165-w260584337",
        "highway": "primary",
        "alt_names": ["dr. Jovana Bijelića"],
        "name": "Jadranska magistrala"
    }, {
        "id": "hghway-3403856111-w236080193",
        "highway": "residential",
        "name": "S. Bajkovica"
    }, {
        "id": "hghway-1953859762-w93079367",
        "highway": "residential",
        "name": "Banjalučka"
    }, {
        "id": "hghway-3784076610-w57784221",
        "highway": "residential",
        "name": "13 jul"
    }, {
        "id": "hghway-1474381128-w236080191",
        "highway": "residential",
        "name": "Nikole Ljubibratića"
    }, {
        "id": "hghway-0844733397-w163212760",
        "highway": "residential",
        "name": "13 jul"
    }, {
        "id": "hghway-1818332359-w145925799",
        "highway": "pedestrian",
        "name": "trg Maršala Tita"
    }, {
        "id": "hghway-3391963727-w146176811",
        "highway": "residential",
        "name": "ulica Nikole Ljubibratić"
    }, {
        "id": "hghway-4050397079-w178207546",
        "highway": "residential",
        "name": "Nikole Ljubibratića"
    }, {
        "id": "hghway-2716506431-w145925686",
        "highway": "pedestrian",
        "alt_names": ["Šetalište 5 Danica", "Шеталиште 5 Даница"],
        "name": "Šetalište 5 Danica"
    }, {
        "id": "hghway-1538871895-w162094665",
        "highway": "steps",
        "name": "stepenište Danica Tomaševiča"
    }, {
        "id": "hghway-2369893582-w163556919",
        "highway": "path",
        "name": "Bajer"
    }, {
        "id": "hghway-2769092750-w146037850",
        "highway": "primary",
        "name": "Jadranska magistrala"
    }, {
        "id": "hghway-1687252834-w93079365",
        "highway": "residential",
        "name": "Orjenskog Bataljona"
    }, {
        "id": "hghway-2512493090-w178374583",
        "highway": "residential",
        "name": "put Partizanskih Majki"
    }, {
        "id": "hghway-3713242290-w146176809",
        "highway": "residential",
        "name": "put Partizanski Majki"
    }],
    "nearest_place": {
        "id": "plcpnt-3505656293-n1752275615",
        "alt_names": ["Marići", "Marici"],
        "name": "Marići",
        "place": "hamlet"
    },
    "refs": {
        "local_admin": "admbnd-3282782267-r2187901",
        "admin0": "admbnd-1757125981-r53296",
        "locality": "plcbnd-3053943461-w186964570"
    },
    "street_name": "Орјенског Батаљона Orjenskog Bataljona",
    "tags": {
        "building": "yes",
        "addr:street": "Орјенског Батаљона Orjenskog Bataljona",
        "addr:housenumber": "21"
    },
    "type": "adrpnt"
}


```
