name := "primes-play-webapp"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  jdbc,
  anorm,
  cache
)     

libraryDependencies ++= Seq(
   "org.reactivemongo" %% "reactivemongo" % "0.10.0",
   "fr.janalyse" %% "primes" % "1.0.6"  
)

resolvers ++= Seq(
  "JAnalyse Repository" at "http://www.janalyse.fr/repository/",
  "Typesafe repository releases" at "http://repo.typesafe.com/typesafe/releases/"
)


play.Project.playScalaSettings
