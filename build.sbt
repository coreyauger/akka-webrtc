import sbt.Keys._

import org.scalajs.sbtplugin.ScalaJSPlugin

import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

import sbt._

packageDescription in Debian := "surkit.io"

maintainer in Debian := "Corey Auger coreyauger@gmail.com"

name := """akka-webrtc"""

version := "0.0.1-SNAPSHOT"

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

scalacOptions in (Compile,doc) := Seq()   

resolvers in ThisBuild += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers in ThisBuild += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

val scalajsOutputDir = Def.settingKey[File]("directory for javascript files output by scalajs")

val sharedScalaDir = file(".") / "shared" / "main" / "scala"

val copySourceMapsTask = Def.task {
  val scalaFiles = (Seq(sharedScalaDir, clientWebRTC.base) ** "*.scala").get
  for (scalaFile <- scalaFiles) {
    val target = new File((classDirectory in Compile).value, scalaFile.getPath)
    IO.copyFile(scalaFile, target)
  }
}

lazy val server =
  (project in file("server"))
    .settings(serverSettings:_*)
    .settings(sharedDirSettings:_*)
 //   .dependsOn(clientWebRTC)
    .aggregate(clientWebRTC)

lazy val clientWebRTC =
  project.in(file("client-webrtc"))
    .settings(clientWebRTCSettings:_*)
    .enablePlugins(ScalaJSPlugin)
    .dependsOn(scalaJsWebRTC, scalaJsDom)

lazy val scalaJsWebRTC = ProjectRef(uri("../scala-js-webrtc"),"root")

lazy val scalaJsDom = uri("../scala-js-dom")

lazy val commonSettings = Seq(
    organization := "im.surfkit",
    version := "0.0.1-SNAPSHOT",
    name := "surfkit",
    scalaVersion := "2.11.7"
  )

lazy val clientWebRTCSettings = commonSettings ++ Seq(
  name := "client-webrtc",
  libraryDependencies ++= clientDeps.value
) ++ sharedDirSettings

lazy val clientDeps = Def.setting(Seq(
  "com.github.japgolly.scalajs-react" %%% "core"             % "0.9.0",
  "com.github.japgolly.scalajs-react" %%% "test"             % "0.9.0"   % "test",
  "com.github.japgolly.scalajs-react" %%% "ext-scalaz71"     % "0.9.0",
  "com.github.japgolly.scalajs-react" %%% "extra"            % "0.9.0",
  "com.github.japgolly.scalacss"      %%% "core"             % "0.3.0",
  "com.github.japgolly.scalacss"      %%% "ext-react"        % "0.3.0",
  "com.lihaoyi"                       %%% "scalatags"        % "0.5.1",
  "com.lihaoyi"                       %%% "upickle"          % "0.3.4"
))

lazy val serverSettings = commonSettings ++ Seq(
  name := "server",
  scalajsOutputDir := (classDirectory in Compile).value / ".." / ".." / ".." / "src" / "main" / "resources" / "js",
  compile in Compile <<= (compile in Compile) dependsOn (fastOptJS in (clientWebRTC, Compile)) dependsOn copySourceMapsTask,
//  dist <<= dist dependsOn (fullOptJS in (clientWebRTC, Compile)),
//  stage <<= stage dependsOn (fullOptJS in (clientWebRTC, Compile)),
  libraryDependencies ++= serverDeps
) ++ (
  Seq(fastOptJS, fullOptJS) map { packageJSKey =>
      crossTarget in (clientWebRTC, Compile, packageJSKey) := scalajsOutputDir.value
    }
  ) ++ sharedDirSettings

lazy val sharedDirSettings = Seq(
  unmanagedSourceDirectories in Compile +=
    baseDirectory.value / "shared" / "main" / "scala"
)

lazy val serverDeps = {
  val akkaV = "2.4.0"
  val akkaStreamV = "1.0"
  Seq(
    "com.typesafe.akka"        %% "akka-actor"                         % akkaV,
    "com.typesafe.akka"        %% "akka-cluster-tools"                 % akkaV,
    "com.typesafe.akka"        %% "akka-cluster-sharding"              % akkaV,
    "com.typesafe.akka"        %% "akka-persistence"                   % akkaV,
    "org.iq80.leveldb"          % "leveldb"                            % "0.7",
    "org.fusesource.leveldbjni" % "leveldbjni-all"                     % "1.8",
    "com.typesafe.akka"        %% "akka-stream-experimental"           % akkaStreamV,
    "com.typesafe.akka"        %% "akka-http-core-experimental"        % akkaStreamV,
    "com.typesafe.akka"        %% "akka-http-experimental"             % akkaStreamV,
    "com.lihaoyi"              %% "upickle"                            % "0.3.4"
  )
}

// debug
val exportFullResolvers = taskKey[Unit]("debug resolvers")

exportFullResolvers := {
  for {
    (resolver,idx) <- fullResolvers.value.zipWithIndex
  } println(s"${idx}.  ${resolver.name}")
}

