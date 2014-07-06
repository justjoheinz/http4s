package org.http4s.examples

import scalaz.concurrent.Task
import scalaz.stream.Process, Process.{Get => _, _}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import org.http4s._
import org.http4s.dsl._
import scodec.bits.ByteVector

object ExampleService extends Http4s {
  val flatBigString = (0 until 1000).map{ i => s"This is string number $i" }.foldLeft(""){_ + _}
  val MyVar = AttributeKey[Int]("org.http4s.examples.myVar")

  def service(implicit executionContext: ExecutionContext = ExecutionContext.global): HttpService = {

    case Get -> Root / "ping" =>
      Ok("pong")

    case req @ Get -> Root / "push" =>
      val data = <html><body><img src="image.jpg"/></body></html>
      Ok(data).push("/image.jpg")(req)

    case req @ Get -> Root / "image.jpg" =>   // Crude: stream doesn't have a binary stream helper yet
      StaticFile.fromResource("/nasa_blackhole_image.jpg", Some(req))
        .map(Task.now)
        .getOrElse(NotFound(req))

    case req @ Post -> Root / "echo" =>
      Task.now(Response(body = req.body))

    case req @ Post -> Root / "echo2" =>
      Task.now(Response(body = req.body.map { chunk =>
        chunk.slice(6, chunk.length)
      }))

    case req @ Post -> Root / "sum"  =>
      text(req).flatMap{ s =>
        val sum = s.split('\n').filter(_.length > 0).map(_.trim.toInt).sum
        Ok(sum)
      }

    case req @ Post -> Root / "shortsum"  =>
      text(req, limit = 3).flatMap { s =>
        val sum = s.split('\n').map(_.toInt).sum
        Ok(sum)
      } handle { case EntityTooLarge(_) =>
        Ok("Got a nonfatal Exception, but its OK").run
      }

/*
    case req @ Post -> Root / "trailer" =>
      trailer(t => Ok(t.headers.length))

    case req @ Post -> Root / "body-and-trailer" =>
      for {
        body <- text(req.charset)
        trailer <- trailer
      } yield Ok(s"$body\n${trailer.headers("Hi").value}")
*/

    case Get -> Root / "html" =>
      Ok(
        <html><body>
          <div id="main">
            <h2>Hello world!</h2><br/>
            <h1>This is H1</h1>
          </div>
        </body></html>
      )

    case req@ Post -> Root / "challenge" =>
      val body = req.body.map { c => new String(c.toArray, req.charset.charset) }.toTask

      body.flatMap{ s: String =>
        if (!s.startsWith("go")) {
          Ok("Booo!!!")
        } else {
          Ok(emit(s) ++ repeatEval(body))
        }
      }
/*
    case req @ Get -> Root / "stream" =>
      Ok(Concurrent.unicast[ByteString]({
        channel =>
          new Thread {
            override def run() {
              for (i <- 1 to 10) {
                channel.push(ByteString("%d\n".format(i), req.charset.name))
                Thread.sleep(1000)
              }
              channel.eofAndEnd()
            }
          }.start()

      }))
  */
    case Get -> Root / "bigstring" =>
      Ok((0 until 1000).map(i => s"This is string number $i"))

    case Get -> Root / "bigfile" =>
      val size = 40*1024*1024   // 40 MB
      Ok(new Array[Byte](size))

    case Get -> Root / "future" =>
      Ok(Future("Hello from the future!"))

    case req @ Get -> Root / "bigstring2" =>
      Ok(Process.range(0, 1000).map(i => s"This is string number $i"))

    case req @ Get -> Root / "bigstring3" => Ok(flatBigString)

    case Get -> Root / "contentChange" =>
      Ok("<h2>This will have an html content type!</h2>", MediaType.`text/html`)

    case req @ Post -> Root / "challenge" =>
      val parser = await1[ByteVector] map {
        case bits if (new String(bits.toArray, req.charset.charset)).startsWith("Go") =>
          Task.now(Response(body = emit(bits) fby req.body))
        case bits if (new String(bits.toArray, req.charset.charset)).startsWith("NoGo") =>
          BadRequest("Booo!")
        case _ =>
          BadRequest("no data")
      }
      (req.body |> parser).eval.toTask

    case req @ Get -> Root / "root-element-name" =>
      xml(req).flatMap(root => Ok(root.label))

    case req @ Get -> Root / "sleep" / time =>
      val duration = time.toInt.milliseconds
      Ok(awakeEvery(duration).map(_ => "tick\n"))
        .addHeader(Header.`Transfer-Encoding`(TransferCoding.chunked))

    case req @ Get -> Root / "fail" =>
      sys.error("fail")

    case req => NotFound(req)
  }
}