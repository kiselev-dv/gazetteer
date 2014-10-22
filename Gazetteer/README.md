gazetteer
=========

Takes osm file and create html/json output for every feature with crossreferences. 

With next goals in mind:
* Easy to deploy (keep number of external dependencyes as low as possible)
* Memory friendly (most parts can be easily rewrited, to use file indexes and save ram)
* Clusterization friendly (build whole process as a number of tasks, which can be performed in multi-thread/multi-node environment)
 

Small demo: http://map.osm.me

Data generated with Gazetteer, search api builded with restexpress and elasticsearch (GazetteerWeb).


compile with apache maven
------------------

    mvn clean compile assembly:single -f Gazetteer/pom.xml
  

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

After join you get number of stripe####.gjson files in your data directory.

Each file contains one feature per line encoded as json (geo json compatible)

Address point feature example 

```javascript
	
    {
        //feature id
        "id": "adrpnt-4277082304-w48588743",
        
        //feature type
        "ftype": "adrpnt",
        
        //creation timestamp
        "timestamp": "2014-03-29T20:56:19.024Z",
        
        //streets in ~500m radius
        "nearbyStreets": [{
            
            //with their ids
            "id": "hghway-0017959611-w48374817",
            
            //and tags
            "properties": {
                "highway": "tertiary",
                "name": "улица Ленина",
                "cladr:suffix": "Улица",
                "cladr:name": "Ленина",
                "cladr:code": "74000013000001300",
                "maxspeed": "40"
            }
        }, {
            "id": "hghway-0486126173-w48380791",
            "properties": {
                "highway": "tertiary",
                "name": "улица 40 лет Октября",
                "cladr:suffix": "Улица",
                "cladr:name": "40 лет Октября",
                "cladr:code": "74000013000000100",
                "maxspeed": "40"
            }
        }],
        
        //original tags of object
        "properties": {
            "addr:street2": "улица 40 лет Октября",
            "addr:postcode": "456770",
            "building": "yes",
            "addr:housenumber2": "4",
            "addr:country": "RU",
            "addr:region": "Челябинская область",
            "addr:street": "улица Ленина",
            "addr:city": "Снежинск",
            "addr:housenumber": "2"
        },
        
        //nearest city (it doesn't matter that city boundary actualy contains addr point)
        "nearestCity": {
            "id": "plcdln-1311766246-n292885144",
            "properties": {
                "name:en": "Snezhinsk",
                "name:ru": "Снежинск",
                "old_name:en": "Chelyabinsk-70",
                "addr:district": "Снежинский городской округ",
                "name:de": "Sneschinsk",
                "is_in": "Chelyabinsk Oblast, Russia",
                "old_name": "Челябинск-70",
                "okato:user": "75545",
                "official_status": "ru:город",
                "addr:country": "RU",
                "int_name": "Snezhinsk",
                "name": "Снежинск",
                "wikipedia": "ru:Снежинск",
                "contact:website": "http://www.snzadm.ru",
                "addr:region": "Челябинская область",
                "place": "town",
                "note": "see wikipedia ru:ЗАТО en:ZATO",
                "population": "49116"
            }
        },
        
        //geojson compatible
        "type": "Feature",
        
        //addresses array
        "addresses": [{
        
            //each address contains full text representation (maybe I'll add some more)
            //with shor notation
            "text": "улица Ленина, 2, Снежинск, Снежинск, Снежинский городской округ",
            
            //address by parts. parts have same sortinag as full text representation
            "parts": [{
            
                // This levels are generated based on centroid in boundaries join
                // In later versions I'll add links to originally joined boundaries
                // Like I do it for pois. (See below)
                
                //level of address part
                "lvl": "street",
                
                //id of object which represent this level (if was found)
                "lnk": "hghway-0017959611-w48374817",
                
                //all *name* tags of linked object
                "names": {
                    "name": "улица Ленина",
                    "cladr:name": "Ленина"
                },
                
                //name which will be used in full text address
                "name": "улица Ленина",
                
                //this parameter is corresponds to average size of objects
                //locaed on this level. (Streets are bigger then buildings 
                //which are represented by housenumber)
                "lvl-size": 10
            }, {
                "lvl": "hn",
                "names": {},
                "lnk": "adrpnt-4277082304-w48588743",
                "name": "2",
                "lvl-size": 20
            }, {
                "lvl": "place:city",
                "name": "Снежинск",
                "lvl-size": 70
            }, {
                "lvl": "place:town",
                "names": {
                    "old_name:ru": "Челябинск-70",
                    "name:en": "Snezhinsk",
                    "name:ru": "Снежинск",
                    "old_name:en": "Chelyabinsk-70",
                    "name": "Снежинск",
                    "name:de": "Sneschinsk",
                    "old_name": "Челябинск-70"
                },
                "lnk": "plcbnd-2057926048-w48374808",
                "name": "Снежинск",
                "lvl-size": 70
            }, {
                "lvl": "boundary:6",
                "names": {
                    "name": "Снежинский городской округ"
                },
                "lnk": "admbnd-3802341234-r1793195",
                "name": "Снежинский городской округ",
                "lvl-size": 90
            }],
            
            //address scheme which was used. In this example:
            //addr:housenumber + addr:street and
            //addr2:housenumber + addr2:street
            //it addr:hn2
            //addr:hn2-1 (-1) means that this is the first part
            //coded by addr:housenumber + addr:street
            "addr-scheme": "addr:hn2-1"
        }, {
            "text": "улица 40 лет Октября, 4, Снежинск, Снежинский городской округ",
            "parts": [{
                "lvl": "street",
                "lnk": "hghway-0486126173-w48380791",
                "names": {
                    "name": "улица 40 лет Октября",
                    "cladr:name": "40 лет Октября"
                },
                "name": "улица 40 лет Октября",
                "lvl-size": 10
            }, {
                "lvl": "hn",
                "names": {},
                "lnk": "adrpnt-4277082304-w48588743",
                "name": "4",
                "lvl-size": 20
            }, {
                "lvl": "place:town",
                "names": {
                    "old_name:ru": "Челябинск-70",
                    "name:en": "Snezhinsk",
                    "name:ru": "Снежинск",
                    "old_name:en": "Chelyabinsk-70",
                    "name": "Снежинск",
                    "name:de": "Sneschinsk",
                    "old_name": "Челябинск-70"
                },
                "lnk": "plcbnd-2057926048-w48374808",
                "name": "Снежинск",
                "lvl-size": 70
            }, {
                "lvl": "boundary:6",
                "names": {
                    "name": "Снежинский городской округ"
                },
                "lnk": "admbnd-3802341234-r1793195",
                "name": "Снежинский городской округ",
                "lvl-size": 90
            }],
            "addr-scheme": "addr:hn2-2"
        }],
        
        
        //Information about original osm object
        "metainfo": {
            
            //id
            "id": 48588743,
            
            //type: node, way, relation
            "type": "way",
            
            //original geometry (for addresses I work with centroids)
            //so this'is the original polygon geometry
            "fullGeometry": {
                "type": "Polygon",
                "coordinates": [
                    [
                        [60.7429887, 56.0856019],
                        [60.7432874, 56.0855888],
                        [60.7431985, 56.0849593],
                        [60.7427618, 56.0849785],
                        [60.7427794, 56.0851026],
                        [60.7429173, 56.0850966],
                        [60.7429887, 56.0856019]
                    ]
                ]
            }
        },
        
        //feature geometry for most objects - point
        //for highways - Linestring
        //for boundaries - slices of polygon
        "geometry": {
            "type": "Point",
            "coordinates": [60.74305285, 56.08524968]
        }
    }

```

POI point feature example 

```javascript
	

    {
        //id
        "id": "poipnt-2949498088-n631427811",
        
        //type
        "ftype": "poipnt",
        
        //creation timestamp
        "timestamp": "2014-03-29T20:56:19.868Z",
        
        //type accordingly to osm doc catalog (see osm.ru poi catalog and osm-doc repository)
        "poiTypes": ["clothes"],
        
        //boundaries which contains this feature
        "boundaries": {
        
            //with joined lvels
            "text": "Снежинск, Снежинский городской округ",
            
            //near the same as for addresses in address feature type
            "parts": [{
                "lvl": "place:town",
                "names": {
                    "old_name:ru": "Челябинск-70",
                    "name:en": "Snezhinsk",
                    "name:ru": "Снежинск",
                    "old_name:en": "Chelyabinsk-70",
                    "name": "Снежинск",
                    "name:de": "Sneschinsk",
                    "old_name": "Челябинск-70"
                },
                "lnk": "plcbnd-2057926048-w48374808",
                "name": "Снежинск",
                "lvl-size": 70
            }, {
                "lvl": "boundary:6",
                "names": {
                    "name": "Снежинский городской округ"
                },
                "lnk": "admbnd-3802341234-r1793195",
                "name": "Снежинский городской округ",
                "lvl-size": 90
            }]
        },
        
        //addresses whic was joined for this poi
        
        // may contains
        // JSONObject sameSource: Same source - it's when we have poi tags and addr tags on a same geometry
        
        // JSONObject nearest: Nearest addr point
        
        // Set<JSONObject> contains: Poi contains addr points or geometry with addr tags contains poi
        
        // Set<JSONObject> shareBuildingWay
        // Represent situation when poi point is a part of bulding way (poi is on entrance) 
		// and other entrances have their own addresses 
		
		// Set<JSONObject> nearestShareBuildingWay
		// Near the same as shareBuildingWay but poi point is inside building
		// and different entrances have different addresses.
		// In some regions poi address will have address 
		// with all shared entrances house numbers range like
		// hn4-hn9, SomeStreet
		// Say hello to Kaliningrad (Kenigsberg).
		
        "joinedAddresses": {
            "nearestShareBuildingWay": [],
            "shareBuildingWay": [],
            "contains": [{
                //each link contains
                //id
                "id": "adrpnt-1345428882-w48588763",
                
                //original properties of addr source obj
                "properties": {
                    "addr:street2": "бульвар Циолковского",
                    "addr:postcode": "456770",
                    "building": "yes",
                    "addr:housenumber2": "9",
                    "addr:country": "RU",
                    "addr:region": "Челябинская область",
                    "addr:street": "улица Васильева",
                    "addr:city": "Снежинск",
                    "addr:housenumber": "18"
                },
                
                //copy of addresses of addrpnt feature
                "addresses": [{
                    "text": "улица Васильева, 18, Снежинск, Снежинск, Снежинский городской округ",
                    "parts": [{
                        "lvl": "street",
                        "lnk": "hghway-0030406789-w48368876",
                        "names": {
                            "name": "улица Васильева",
                            "cladr:name": "Васильева"
                        },
                        "name": "улица Васильева",
                        "lvl-size": 10
                    }, {
                        "lvl": "hn",
                        "names": {},
                        "lnk": "adrpnt-1345428882-w48588763",
                        "name": "18",
                        "lvl-size": 20
                    }, {
                        "lvl": "place:city",
                        "name": "Снежинск",
                        "lvl-size": 70
                    }, {
                        "lvl": "place:town",
                        "names": {
                            "old_name:ru": "Челябинск-70",
                            "name:en": "Snezhinsk",
                            "name:ru": "Снежинск",
                            "old_name:en": "Chelyabinsk-70",
                            "name": "Снежинск",
                            "name:de": "Sneschinsk",
                            "old_name": "Челябинск-70"
                        },
                        "lnk": "plcbnd-2057926048-w48374808",
                        "name": "Снежинск",
                        "lvl-size": 70
                    }, {
                        "lvl": "boundary:6",
                        "names": {
                            "name": "Снежинский городской округ"
                        },
                        "lnk": "admbnd-3802341234-r1793195",
                        "name": "Снежинский городской округ",
                        "lvl-size": 90
                    }],
                    "addr-scheme": "addr:hn2-1"
                }, {
                    "text": "бульвар Циолковского, 9, Снежинск, Снежинский городской округ",
                    "parts": [{
                        "lvl": "street",
                        "lnk": "hghway-0112649536-w48504343",
                        "names": {
                            "name": "бульвар Циолковского",
                            "cladr:name": "Циолковского"
                        },
                        "name": "бульвар Циолковского",
                        "lvl-size": 10
                    }, {
                        "lvl": "hn",
                        "names": {},
                        "lnk": "adrpnt-1345428882-w48588763",
                        "name": "9",
                        "lvl-size": 20
                    }, {
                        "lvl": "place:town",
                        "names": {
                            "old_name:ru": "Челябинск-70",
                            "name:en": "Snezhinsk",
                            "name:ru": "Снежинск",
                            "old_name:en": "Chelyabinsk-70",
                            "name": "Снежинск",
                            "name:de": "Sneschinsk",
                            "old_name": "Челябинск-70"
                        },
                        "lnk": "plcbnd-2057926048-w48374808",
                        "name": "Снежинск",
                        "lvl-size": 70
                    }, {
                        "lvl": "boundary:6",
                        "names": {
                            "name": "Снежинский городской округ"
                        },
                        "lnk": "admbnd-3802341234-r1793195",
                        "name": "Снежинский городской округ",
                        "lvl-size": 90
                    }],
                    "addr-scheme": "addr:hn2-2"
                }],
                "ftype": "adrpnt"
            }],
            
            //our addres feature founded twice (ascontains and as nearest)
            "nearest": {
                "id": "adrpnt-1345428882-w48588763",
                "properties": {
                    "addr:street2": "бульвар Циолковского",
                    "addr:postcode": "456770",
                    "building": "yes",
                    "addr:housenumber2": "9",
                    "addr:country": "RU",
                    "addr:region": "Челябинская область",
                    "addr:street": "улица Васильева",
                    "addr:city": "Снежинск",
                    "addr:housenumber": "18"
                },
                "addresses": [{
                    "text": "улица Васильева, 18, Снежинск, Снежинск, Снежинский городской округ",
                    "parts": [{
                        "lvl": "street",
                        "lnk": "hghway-0030406789-w48368876",
                        "names": {
                            "name": "улица Васильева",
                            "cladr:name": "Васильева"
                        },
                        "name": "улица Васильева",
                        "lvl-size": 10
                    }, {
                        "lvl": "hn",
                        "names": {},
                        "lnk": "adrpnt-1345428882-w48588763",
                        "name": "18",
                        "lvl-size": 20
                    }, {
                        "lvl": "place:city",
                        "name": "Снежинск",
                        "lvl-size": 70
                    }, {
                        "lvl": "place:town",
                        "names": {
                            "old_name:ru": "Челябинск-70",
                            "name:en": "Snezhinsk",
                            "name:ru": "Снежинск",
                            "old_name:en": "Chelyabinsk-70",
                            "name": "Снежинск",
                            "name:de": "Sneschinsk",
                            "old_name": "Челябинск-70"
                        },
                        "lnk": "plcbnd-2057926048-w48374808",
                        "name": "Снежинск",
                        "lvl-size": 70
                    }, {
                        "lvl": "boundary:6",
                        "names": {
                            "name": "Снежинский городской округ"
                        },
                        "lnk": "admbnd-3802341234-r1793195",
                        "name": "Снежинский городской округ",
                        "lvl-size": 90
                    }],
                    "addr-scheme": "addr:hn2-1"
                }, {
                    "text": "бульвар Циолковского, 9, Снежинск, Снежинский городской округ",
                    "parts": [{
                        "lvl": "street",
                        "lnk": "hghway-0112649536-w48504343",
                        "names": {
                            "name": "бульвар Циолковского",
                            "cladr:name": "Циолковского"
                        },
                        "name": "бульвар Циолковского",
                        "lvl-size": 10
                    }, {
                        "lvl": "hn",
                        "names": {},
                        "lnk": "adrpnt-1345428882-w48588763",
                        "name": "9",
                        "lvl-size": 20
                    }, {
                        "lvl": "place:town",
                        "names": {
                            "old_name:ru": "Челябинск-70",
                            "name:en": "Snezhinsk",
                            "name:ru": "Снежинск",
                            "old_name:en": "Chelyabinsk-70",
                            "name": "Снежинск",
                            "name:de": "Sneschinsk",
                            "old_name": "Челябинск-70"
                        },
                        "lnk": "plcbnd-2057926048-w48374808",
                        "name": "Снежинск",
                        "lvl-size": 70
                    }, {
                        "lvl": "boundary:6",
                        "names": {
                            "name": "Снежинский городской округ"
                        },
                        "lnk": "admbnd-3802341234-r1793195",
                        "name": "Снежинский городской округ",
                        "lvl-size": 90
                    }],
                    "addr-scheme": "addr:hn2-2"
                }],
                "ftype": "adrpnt"
            }
        },
        
        //original poi properties
        "properties": {
            "shop": "clothes",
            "name": "Радужный"
        },
        
        //geojson compatibility
        "type": "Feature",
        
        //osm information
        "metainfo": {
            "id": 631427811,
            "type": "node"
        },
        
        //centroid geometry
        "geometry": {
            "type": "Point",
            "coordinates": [60.737858, 56.0873401]
        }
    }


```
