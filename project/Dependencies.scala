import sbt._

object Dependencies {

  object Resolvers {
    val typesafe = "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
    val sonatype = "Sonatype Release" at "https://oss.sonatype.org/content/repositories/releases"
    val mvnrepository = "MVN Repo" at "http://mvnrepository.com/artifact"
    val litbitBitbucket = "Litbit Repo" at "https://s3-us-west-2.amazonaws.com/maven.litbit.com/snapshots"
    val emuller = "emueller-bintray" at "http://dl.bintray.com/emueller/maven"

    val allResolvers = Seq(typesafe, sonatype, mvnrepository, emuller, litbitBitbucket)

  }

  def compile(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")

  def provided(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")

  def test(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test")

  def runtime(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "runtime")

  def container(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "container")

  val fey             = "org.apache.iota"     %% "fey-core"                   % "1.0-SNAPSHOT"
  val zmq             = "org.zeromq"          %  "jeromq"                     % "0.3.5"
  val math3           = "org.apache.commons"  %  "commons-math3"              % "3.2"
  val codec           = "commons-codec"       % "commons-codec"               % "1.10"
  val apacheIO        = "commons-io"          % "commons-io"                  % "2.4"

  val akka_actor      = "com.typesafe.akka"   %% "akka-actor"                 % "2.4.2"
  val typesafe_config = "com.typesafe"        %  "config"                     % "1.3.0"

  val playJson        = "com.typesafe.play"   %% "play-json"                  % "2.5.3"
  val jsonValidator   = "com.eclipsesource"   %% "play-json-schema-validator" % "0.7.0"

  //Logger
  val slf4j           = "com.typesafe.akka"   %% "akka-slf4j"                 % "2.4.2"
  val log4jbind       = "ch.qos.logback"      %  "logback-classic"            % "1.1.7"
  val javaFilter      = "janino" % "janino"   %  "2.5.10"

  //restapi
  val sprayCan        = "io.spray"            %%  "spray-can"                 % "1.3.3"
  val sprayRouting    = "io.spray"            %%  "spray-routing"             % "1.3.3"


}
