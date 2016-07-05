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

  val akka_actor = "com.typesafe.akka" %% "akka-actor" % "2.4.2"
  val fey = "org.apache.iota" %% "fey-core" % "1.0-SNAPSHOT"
  val zmq = "org.zeromq" % "jeromq" % "0.3.5"
}
