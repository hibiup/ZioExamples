package com.hibiup.zio.akka

import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.Materializer
import cats.effect.Resource
import zio.{Task, ZEnv, ZIO}
import com.hibiup.zio.akka.config.{AkkaActorSystem, Configuration, HasActorSystem}
import com.hibiup.zio.akka.repositories.{HasTransactor, Persistence}
import com.typesafe.scalalogging.StrictLogging
import doobie.util.ExecutionContexts
import zio.interop.catz._
import zio.blocking.Blocking

import scala.concurrent.ExecutionContext

object MainEntry extends zio.App with StrictLogging{
    import com.hibiup.zio.akka.routes.HomeController.routes
    import AkkaActorSystem.DSL._
    import Persistence.DSL._

    implicit val ecResource: Resource[Task, ExecutionContext] = ExecutionContexts.fixedThreadPool[Task](5)

    override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] = {
        val program:ZIO[HasActorSystem with HasTransactor, Throwable, ServerBinding] = for {
            tnx <- transactor   // Supported HasTransactor (Persistent)
            handle <- actorSystem >>= {implicit actorSystem =>
                implicit val mat: Materializer = Materializer(actorSystem)
                Task.fromFuture{_ =>
                  logger.info("Akka http is starting...")
                    Http().bindAndHandle(routes(actorSystem), "0.0.0.0", 9000)
                }
            }
            _ <- Task.never
        } yield {
            handle
        }

        ecResource.use(ec =>
            program.provideLayer(AkkaActorSystem.live ++
              ((Configuration.live ++ Blocking.live) >>> Persistence.live(ec)))
        ).map(handle => handle.unbind()).fold(_ => 1, _ => 0)
    }
}
