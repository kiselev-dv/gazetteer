gazetteer
=========

compile with maven

    mvn clean compile assembly:single
  
usage

    java -jar gazetter.jar slice [--slices-dir SLICES_DIR=slices] input-file.osm
    java -jar gazetter.jar join [--slices-dir SLICES_DIR=slices] 
    java -jar gazetter.jar out [--slices-dir SLICES_DIR=slices] {--out-json | --out-csv }
  
