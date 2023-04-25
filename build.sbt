import org.scalajs.linker.interface.ModuleSplitStyle

ThisBuild / scalaVersion := "3.2.2"
ThisBuild / fork := true
Global / cancelable := true
ThisBuild / connectInput := true

Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val root = project
  .settings(
    name := "faisal-helper",
    version := "0.1.0-SNAPSHOT"
  )
  .aggregate(shared, api, web)

lazy val api = project
  .in(file("api"))
  .enablePlugins(JavaAppPackaging)
  .settings(
    name := "faisal-helper-api",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % "2.0.13",
      "dev.zio" %% "zio-streams" % "2.0.13",
      "dev.zio" %% "zio-http" % "3.0.0-RC1",
      "dev.zio" %% "zio-json" % "0.5.0",
      "com.sun.mail" % "jakarta.mail" % "2.0.1",
      "org.scalameta" %% "munit" % "0.7.29" % Test
    )
  )
  .dependsOn(shared)

lazy val shared = project
  .in(file("shared"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "faisal-helper-shared",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % "2.0.13",
      "dev.zio" %% "zio-json" % "0.5.0"
    )
  )

lazy val web = project
  .in(file("web"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "faisal-helper-web",
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.ESModule)
        .withModuleSplitStyle(
          ModuleSplitStyle.SmallModulesFor(List("faisalHelper.web"))
        )
    },
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-json" % "0.5.0",
      "com.raquo" %%% "laminar" % "15.0.1",
      "org.scala-js" %%% "scalajs-dom" % "2.4.0",
      "io.laminext" %%% "core" % "0.15.0",
      "io.laminext" %%% "fetch" % "0.15.0"
    )
  )
  .dependsOn(shared)
