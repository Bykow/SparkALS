// build.sbt - Build definition for sbt
// See https://www.scala-sbt.org/1.x/docs/Basic-Def.html
// Here we are using a bare .sbt build definition.

// Name of the project
name := "Hello-world"

// Version of the project
version := "1.0"

// sbt should use this Scala version to build the project
scalaVersion := "2.11.12"

// sbt should invoke the Scala compiler with these options
scalacOptions ++= Seq("-deprecation")

val sparkVersion = "2.2.1"

libraryDependencies ++= Seq(
  // these dependencies are needed for a build
  "org.apache.spark" %% "spark-core" % sparkVersion,
  "org.apache.spark" %% "spark-sql" % sparkVersion,
  //"org.apache.spark" %% "spark-streaming" % sparkVersion,
  //"org.apache.spark" %% "spark-streaming-twitter" % sparkVersion,

  // these dependencies are needed for a test build
  "junit" % "junit" % "4.12" % "test",
  "org.scalatest" %% "scalatest" % "3.0.4" % "test",

  // Stanford Core Nlp provides a lemmatize
  "edu.stanford.nlp" % "stanford-corenlp" % "3.4",
  "edu.stanford.nlp" % "stanford-corenlp" % "3.4" classifier "models",
  "edu.stanford.nlp" % "stanford-parser" % "3.4"
)