import sbt.Keys.{javaOptions, javacOptions}
import sbtassembly.AssemblyPlugin.autoImport.assemblyOption

lazy val commonSettings = Seq(
    organization := "me.osm",
    name := name.value,
    version := "0.1-SNAPSHOT",

    scalaVersion := "2.12.8",
    scalacOptions ++= Seq("-Xlint", "-feature", "-deprecation", "-target:jvm-1.7"),

    javacOptions ++= Seq("-source", "1.7", "-target", "1.7", "-Xlint:unchecked", "-Xlint:deprecation"),
    javaOptions ++= Seq("-Xms512M", "-Xmx2048M", "-XX:+CMSClassUnloadingEnabled"),

    test in assembly := {},
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false),

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
        "com.sun.xml.bind" % "jaxb-impl" % "2.2.7",

        "junit" % "junit" % "4.11" % "test"
    )
)

lazy val `external-sorting` = (project in file("external-sorting")).
    settings(commonSettings)

lazy val `osm-doc-java` = (project in file("osm-doc-java")).
    settings(commonSettings)

lazy val gazetteer = (project in file("gazetteer")).
    settings(commonSettings).
    settings(
        libraryDependencies ++= Seq("com.vividsolutions" % "jts" % "1.13"),
        excludeDependencies in IntegrationTest += "org.slf4j" % "slf4j-log4j12"
    ).
    dependsOn(`external-sorting`, `osm-doc-java`)

//lazy val root = (project in file(".")).
//    aggregate(
//        `external-sorting`,
//        `osm-doc-java`,
//        gazetteer
//    ).
//    settings(commonSettings).
//    settings(
//        name := "gazetteer-lib",
//        unmanagedSourceDirectories in Compile := Nil,
//        unmanagedResourceDirectories in Compile := Nil,
//        unmanagedSourceDirectories in Test := Nil,
//        unmanagedResourceDirectories in Test := Nil,
//        sbt.Keys.`package` := target.value,
//        publish := {},
//        publishLocal := {}
//    )
