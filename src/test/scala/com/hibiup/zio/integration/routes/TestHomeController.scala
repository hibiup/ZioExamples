package com.hibiup.zio.integration.routes

import akka.actor.ActorSystem
import com.hibiup.zio.integration.configuration.{AkkaActorSystem, Configuration}
import com.hibiup.zio.integration.repositories.{Persistence, User, UserService}
import com.typesafe.scalalogging.StrictLogging
import org.http4s._
import org.scalatest.flatspec.AnyFlatSpec
import zio._
import org.http4s.implicits._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.interop.catz._


class TestHomeController extends AnyFlatSpec with StrictLogging{
    import org.http4s.dsl.Http4sDsl
    val dsl = new Http4sDsl[Task]{}
    import dsl._

    val runtime = zio.Runtime.default

    implicit val sys = ActorSystem("test-actorSystem")

    val layers = (Configuration.live ++  Blocking.live) >>>
      Persistence.live(runtime.platform.executor.asEC) >>>
      UserService.live(sys)

    "Home controller get user" should "" in {
        val urlUser = Request[Task](Method.GET, uri"/user/100")
        val notFound = HomeController(layers).route.run(urlUser)

        val program = ZIO.runtime[Clock with HasActorSystem].map{ implicit rt =>
            rt.unsafeRun(notFound.value).map(resp => assert(resp.status.code == 404))
        }

        runtime.unsafeRun(program.provideSomeLayer(Clock.live ++ AkkaActorSystem.live).fold(_ => (), println))
    }

    "Home controller create user" should "" in {
        import io.circe.syntax._
        import org.http4s.circe._
        import io.circe.generic.auto._
        import org.http4s.circe.CirceEntityDecoder._

        val program = ZIO.runtime[Clock with HasActorSystem] >>= { rt =>
            val urlUser = Request[Task](Method.POST, uri"/user")
              .withEntity{
                  val userJson = User(None, Option("John")).asJson
                  logger.debug(userJson.toString())
                  userJson
              }

            for {
                created <- HomeController(layers).route.run(urlUser).value
                userId <- ZIO.fromOption(created.map { t =>
                    rt.unsafeRun(t.as[Int])
                })
            } yield userId
        }

        runtime.unsafeRun(program.provideSomeLayer(Clock.live ++ AkkaActorSystem.live).fold(_ => (), println))
    }
}
