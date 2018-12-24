name := "gazetteer-lib"
organization := "me.osm"
version := "0.1-NAPSHOT"

scalaVersion := "2.12.8"

scalacOptions ++= Seq("-Xlint", "-feature", "-deprecation")
javacOptions ++= Seq("-source", "1.7", "-target", "1.7")
javaOptions ++= Seq("-Xms512M", "-Xmx2048M", "-XX:+CMSClassUnloadingEnabled")

test in assembly := {}
assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false)

libraryDependencies ++= Seq(
    "org.slf4j" % "slf4j-api" % "1.7.12",
    "net.sourceforge.argparse4j" % "argparse4j" % "0.4.3",
    "org.codehaus.groovy" % "groovy-all" % "2.2.2",
    "org.json" % "json" % "20131018",
    "org.apache.lucene" % "lucene-analyzers-common" % "5.1.0",
    "net.sf.trove4j" % "trove4j" % "3.0.3",
    "net.sf.supercsv" % "super-csv-dozer" % "2.1.0",
    "joda-time" % "joda-time" % "2.3",
    "org.apache.commons" % "commons-collections4" % "4.1",
    "org.apache.commons" % "commons-compress" % "1.0",
    "commons-io" % "commons-io" % "1.3.2",
    "commons-codec" % "commons-codec" % "1.4",
    "org.apache.commons" % "commons-lang3" % "3.2.1",
    "com.sun.xml.txw2" % "txw2" % "20110809",

    "com.vividsolutions" % "jts" % "1.13"
)
