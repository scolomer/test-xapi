import sbt._
import sbt.Keys._

import com.typesafe.sbteclipse.plugin.EclipsePlugin._

object TestXapiBuild extends Build {

  lazy val testXapi = Project(
    id = "test-xapi",
    base = file("."),
    settings = Project.defaultSettings ++ Seq(
      name := "Test Xapi",
      organization := "sco",
      version := "0.1-SNAPSHOT",
      scalaVersion := "2.10.4",
      resolvers += "TypeSafe" at "http://repo.typesafe.com/typesafe/releases/",
      libraryDependencies ++= Seq(
        "org.clapper" %% "grizzled-slf4j" % "1.0.1",
        "ch.qos.logback" % "logback-classic" % "1.0.13",
        "org.codehaus.groovy" % "groovy-all" % "2.1.6",
        "com.twitter" %% "util-core" % "6.12.1",
        "com.typesafe.play" %% "play" % "2.2.2",
        "com.typesafe" % "config" % "1.2.0"
      ),
      EclipseKeys.executionEnvironment := Some(EclipseExecutionEnvironment.JavaSE17),
      EclipseKeys.createSrc := EclipseCreateSrc.Default + EclipseCreateSrc.Resource
    )
  )
  
}
