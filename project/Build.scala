import sbt._
import Keys._
import sbtassembly.Plugin._
import sbtassembly.AssemblyUtils._
import AssemblyKeys._

object MyBuild extends Build {

  lazy val project = Project(id = "timing", base = file(".")).
                            settings(projSettings:_*).
                            settings(dependencySettings:_*).
                            settings(assemblyProjSettings:_*)
           
  def projSettings : Seq[Setting[_]] = Defaults.defaultSettings ++ Seq(
    name := "timing",
    organization := "edu.uml.jkraunelis",
    version := "0.1",
    scalaVersion := "2.11.1",
    resolvers += "Akka Repository" at "http://repo.akka.io/releases/",
    javacOptions ++= Seq("-source","1.8","-target","1.8"),
    scalacOptions ++= Seq()
  )

  def assemblyProjSettings : Seq[Setting[_]] = assemblySettings ++ Seq(
    test in assembly := {},
    jarName in assembly := ("timing-spark-assembly-" + version.value + "_" + scalaVersion.value + ".jar"),
    logLevel in assembly := Level.Error,
    mergeStrategy in assembly := conflictRobustMergeStrategy
  )

 def dependencySettings : Seq[Setting[_]] = {
    Seq(
      libraryDependencies ++= Seq(
       "org.apache.spark" %% "spark-core" % "2.1.0" % "provided"
      )
    )
   }

val conflictRobustMergeStrategy: String => MergeStrategy = { 
    case "reference.conf" | "rootdoc.txt" =>
      MergeStrategy.concat
    case PathList("META-INF", xs @ _*) =>
      (xs map {_.toLowerCase}) match {
        case ("manifest.mf" :: Nil) | ("index.list" :: Nil) | ("dependencies" :: Nil) =>
          MergeStrategy.discard
        case ps @ (x :: xs) if ps.last.endsWith(".sf") || ps.last.endsWith(".dsa") =>
          MergeStrategy.discard
        case "plexus" :: xs =>
          MergeStrategy.discard
        case "services" :: xs =>
          MergeStrategy.filterDistinctLines
        case ("spring.schemas" :: Nil) | ("spring.handlers" :: Nil) =>
          MergeStrategy.filterDistinctLines
        case _ => MergeStrategy.first
      }
    case _ => MergeStrategy.first
  }

}