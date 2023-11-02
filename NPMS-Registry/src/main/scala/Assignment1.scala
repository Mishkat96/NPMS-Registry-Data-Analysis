import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{IOResult, OverflowStrategy}
import akka.stream.scaladsl.{Balance, Compression, FileIO, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source}
import akka.stream.{FlowShape, Graph, IOResult, OverflowStrategy}
import akka.util.ByteString
import akka.stream.scaladsl.GraphDSL.Implicits.flow2flow

import java.nio.file.{Path,Paths}
import scala.concurrent.{ExecutionContextExecutor, Future}
import ujson.Arr.from
import ujson.Value.JsonableSeq
import akka.stream.ThrottleMode

import java.nio.file.StandardOpenOption._

import concurrent.duration.DurationInt


object FirstAssignment extends App:
  implicit val actorSystem: ActorSystem = ActorSystem("FirstAssignment")
  implicit val executionContext: ExecutionContextExecutor = actorSystem.dispatcher

  case class NpmsObject(name: String, githubvalues: List[String], evaluationvalues: List[String]) {

    //used this function to get the values from the json files
    def requestApi = {
      println(s"Analysing $name")
      val url = s"https://api.npms.io/v2/package/$name"
      val response = requests.get(url)
      val json = ujson.read(response.data.toString)
      val git = json("collected")("github").toString()
      val eva = json("evaluation").toString()

      NpmsObject(name, githubvalues = List(git), evaluationvalues = List(eva))
    }

    //used this function to discard packages less than 20 stars. Those packages are made null
    def filterStars = {
      val url = s"https://api.npms.io/v2/package/$name"
      val response = requests.get(url)
      val json = ujson.read(response.data.toString)
      val a = json("collected")("github")("starsCount").num
      if (a > 20) {
        NpmsObject(name, githubvalues, evaluationvalues)
      }
      else {
        println(s"Package $name is discarded for having stars less than 20")
        NpmsObject(name,null,null)
      }
    }

    //used this function to discard packages  Those packages are made null
    def discardTests = {
      val url = s"https://api.npms.io/v2/package/$name"
      val response = requests.get(url)
      val json = ujson.read(response.data.toString)
      val a = json("evaluation")("quality")("carefulness").num

      if (a > 0.5) {
        NpmsObject(name, githubvalues, evaluationvalues)
      }
      else {
        println(s"Package $name is discarded for having less than 50% code")
        NpmsObject(name,null,null)
      }
    }

    //used this function to discard packages with a release frequency lower than 20%. Those packages are made null
    def filterFrequency = {
      val url = s"https://api.npms.io/v2/package/$name"
      val response = requests.get(url)
      val json = ujson.read(response.data.toString)
      val a = json("evaluation")("maintenance")("releasesFrequency").num

      if (a > 0.2) {
        NpmsObject(name, githubvalues, evaluationvalues)
      }
      else {
        println(s"Package $name is discarded for having frequency less than 20% code")
        NpmsObject(name, null, null)
      }
    }

    //used this function to discard packages with less than 100 downloads
    def filterDownload = {
      val url = s"https://api.npms.io/v2/package/$name"
      val response = requests.get(url)
      val json = ujson.read(response.data.toString)
      val a = json("evaluation")("popularity")("downloadsCount").num

      if (a > 100) {
        NpmsObject(name, githubvalues, evaluationvalues)
      }
      else {
        println(s"Package $name has been discarded for having less than 100 downloads")
        NpmsObject(name, null, null)
      }
    }
  }

  val path = Paths.get("src/main/resources/packages.txt.gz")
  val source: Source[ByteString, Future[IOResult]] = FileIO.fromPath(path)
  val flowUnzip: Flow[ByteString, ByteString, NotUsed] = Compression.gunzip()
  val convertToString: Flow[ByteString, String, NotUsed] = Flow[ByteString].map(_.utf8String)
  val splitToNewLines: Flow[String, String, NotUsed] = Flow[String].mapConcat(_.split("\n"))
  val streamBuffer = Flow[NpmsObject].buffer(20, OverflowStrategy.backpressure)
  val convertToNpmsObject: Flow[String, NpmsObject, NotUsed] = Flow[String].map(Name => NpmsObject(Name, githubvalues = List(), evaluationvalues = List()))

  //learnt about throttle from a reply from this post
  //https://stackoverflow.com/questions/48097345/dont-completely-understand-source-delay-method-or-there-is-error-in-akka-stream
  val timeDelay = Flow[NpmsObject].throttle(1, 3.second, 100, ThrottleMode.shaping)


  val requestApiApi: Flow[NpmsObject, NpmsObject, NotUsed] = Flow[NpmsObject].map(current => current.requestApi)
  val starFilter: Flow[NpmsObject, NpmsObject, NotUsed] = Flow[NpmsObject].map(current => current.filterStars)
  val testFilter: Flow[NpmsObject, NpmsObject, NotUsed] = Flow[NpmsObject].map(current => current.discardTests)
  val downloadfilter: Flow[NpmsObject, NpmsObject, NotUsed] = Flow[NpmsObject].map(current => current.filterDownload)
  val frequencyfilter: Flow[NpmsObject, NpmsObject, NotUsed] = Flow[NpmsObject].map(current => current.filterFrequency)

  val sinkToTerminal = Sink.foreach(println)
  val resourcesFolder: String = "src/main/resources"
  val sinkToTextFile = FileIO.toPath(Paths.get(s"$resourcesFolder/exercise.txt"), Set(CREATE, WRITE, APPEND))


//can also use this instead of pipelineFilters
  val pipelineFilterss: Flow[NpmsObject,NpmsObject, NotUsed] =
    starFilter
      .via(testFilter)
      .via(downloadfilter)
      .via(frequencyfilter)

//This pipeline is inside the big pipeline through which we discard diferrent packages
  val pipelineFilters: Flow[NpmsObject, NpmsObject, NotUsed] = Flow.fromGraph(
    GraphDSL.create() { implicit builder =>
      val starShape = builder.add(starFilter)
      val testShape = builder.add(testFilter)
      val downloadShape = builder.add(downloadfilter)
      val frequencyShape = builder.add(frequencyfilter)

      starShape ~> testShape ~> downloadShape ~> frequencyShape

      FlowShape(starShape.in, frequencyShape.out)
    })

  val flowThreePipelines: Flow[NpmsObject, NpmsObject, NotUsed] = Flow.fromGraph(
    GraphDSL.create() {
        implicit builder =>
        import GraphDSL.Implicits._

        val balance = builder.add(Balance[NpmsObject](3))
        val merge = builder.add(Merge[NpmsObject](3))

        balance ~> pipelineFilters ~> merge
        balance ~> pipelineFilters ~> merge
        balance ~> pipelineFilters ~> merge

        FlowShape(balance.in, merge.out)
    }
  )

  val runnableGraph: RunnableGraph[Future[IOResult]] =
    source
      .via(flowUnzip)
      .via(convertToString)
      .via(splitToNewLines)
      .via(convertToNpmsObject)
      .via(streamBuffer)
      .via(timeDelay)
      .via(requestApiApi)
      .via(flowThreePipelines)
      .to(sinkToTerminal)

  runnableGraph.run().onComplete(_ => actorSystem.terminate())









