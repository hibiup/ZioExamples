package com.hibiup.zio.akka

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import cats.effect.Resource
import zio.{ExitCode, Task, ZEnv, ZIO}
import com.hibiup.zio.akka.config.{AkkaActorSystem, Configuration, HasActorSystem}
import com.hibiup.zio.akka.repositories.{HasTransactor, Persistence}
import com.hibiup.zio.akka.routes.{HasHomeController, HomeController}
import com.hibiup.zio.akka.services.UserService
import com.typesafe.scalalogging.StrictLogging
import doobie.util.ExecutionContexts
import zio.interop.catz._
import zio.blocking.Blocking

import scala.concurrent.ExecutionContext

object MainEntry extends zio.App with StrictLogging{
    import AkkaActorSystem.DSL._
    import Persistence.DSL._
    import HomeController.DSL._

    implicit val ecResource: Resource[Task, ExecutionContext] = ExecutionContexts.fixedThreadPool[Task](5)

    override def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] = {
        val program:ZIO[HasActorSystem with HasTransactor with HasHomeController, Throwable, ServerBinding] = for {
            tnx <- transactor   // Supports HasTransactor (Persistent)
            sys <- actorSystem
            handle: ServerBinding <- homeRoute(UserService.live(tnx), sys) >>= { route =>
                implicit val s: ActorSystem = sys
                implicit val mat: Materializer = Materializer(sys)
                Task.fromFuture{_ =>
                    Http().bindAndHandle(route, "0.0.0.0", 9000)
                }
            }
            _ <- Task.never
        } yield {
            handle
        }

        ecResource.use(implicit ec =>
            program.provideLayer(AkkaActorSystem.live ++
              ((Configuration.live ++ Blocking.live) >>> Persistence.live(ec) ++ HomeController.live)
            )
        ).map(handle => {handle.unbind()}).exitCode  //.fold(_ => 1, _ => 0)
    }
}
