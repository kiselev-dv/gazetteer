// https://www.scala-sbt.org/1.x/docs/Multi-Project.html

import sbt.Keys.{javaOptions, javacOptions}
import sbtassembly.AssemblyPlugin.autoImport.assemblyOption

lazy val commonSettings = Seq(
    organization := "me.osm",
    name := name.value,
    version := "1.9.0-SNAPSHOT",

    scalaVersion := "2.12.8",
    scalacOptions ++= Seq("-Xlint", "-feature", "-deprecation", "-target:jvm-1.7"),

    javacOptions ++= Seq("-source", "1.7", "-target", "1.7", "-Xlint:unchecked", "-Xlint:deprecation"),
    javaOptions ++= Seq("-Xms512M", "-Xmx2048M", "-XX:+CMSClassUnloadingEnabled"),

    test in assembly := {},
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false),

    libraryDependencies ++= Seq(
        "junit" % "junit" % "4.11" % "test"
    )
)

// v 0.2.0
lazy val `external-sorting` = (project in file("external-sorting"))
    .settings(commonSettings)

// v 1.4.0
lazy val `osm-doc-java` = (project in file("osm-doc-java"))
    .settings(commonSettings)
    .settings(
        libraryDependencies ++= Seq(
            "com.sun.xml.bind" % "jaxb-impl" % "2.2.7",
            "org.apache.commons" % "commons-lang3" % "3.2.1",
            "commons-io" % "commons-io" % "1.3.2",
            "net.sf.supercsv" % "super-csv-dozer" % "2.1.0",
            "net.sourceforge.argparse4j" % "argparse4j" % "0.4.3",
            "org.json" % "json" % "20131018"
        ))

lazy val gazetteer = (project in file("gazetteer"))
    .settings(commonSettings)
    .settings(
        libraryDependencies ++= Seq(
//            "com.google.code.externalsortinginjava" % "externalsortinginjava" % "0.2.0",
            "org.apache.commons" % "commons-compress" % "1.0",
            "org.apache.commons" % "commons-collections4" % "4.1",
            "org.apache.lucene" % "lucene-analyzers-common" % "5.1.0",
            "commons-codec" % "commons-codec" % "1.4",
            "joda-time" % "joda-time" % "2.3",
            "net.sf.trove4j" % "trove4j" % "3.0.3",
            "org.codehaus.groovy" % "groovy-all" % "2.2.2",
            "com.vividsolutions" % "jts" % "1.13"
        )
    )
    .dependsOn(`external-sorting`, `osm-doc-java`)

// running a task on the aggregate project will also run it on the aggregated projects
// compile test package assembly publishLocal
lazy val root = (project in file("."))
    .aggregate(
        `external-sorting`,
        `osm-doc-java`,
        gazetteer
    )
    .settings(commonSettings)
    .settings(
        assembleArtifact in assembly := false,
        name := "gazetteer-deps",
        unmanagedSourceDirectories in Compile := Nil,
        unmanagedResourceDirectories in Compile := Nil,
        unmanagedSourceDirectories in Test := Nil,
        unmanagedResourceDirectories in Test := Nil,
        sbt.Keys.`package` := target.value,
        publish := {},
        publishLocal := {}
    )
//    .disablePlugins(AssemblyPlugin)
