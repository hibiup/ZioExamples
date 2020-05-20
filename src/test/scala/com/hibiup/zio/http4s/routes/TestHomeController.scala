package com.hibiup.zio.http4s.routes

import akka.actor.ActorSystem
import cats.data.OptionT
import com.hibiup.zio.http4s.configuration.{AkkaActorSystem, Configuration}
import com.hibiup.zio.http4s.repositories.{HasTransactor, Persistence, User, UserService}
import com.typesafe.scalalogging.StrictLogging
import org.http4s._
import org.scalatest.flatspec.AnyFlatSpec
import zio._
import org.http4s.implicits._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.interop.catz._
import doobie.Transactor
import Persistence.DSL._


class TestHomeController extends AnyFlatSpec with StrictLogging{
    import org.http4s.dsl.Http4sDsl
    val dsl = new Http4sDsl[Task]{}
    import dsl._

    val runtime = zio.Runtime.default

    implicit val sys = ActorSystem("test-actorSystem")

    val transactorLayer = (Configuration.live ++  Blocking.live) >>> Persistence.live

    "Home controller get user" should "" in {
        val urlUser = Request[Task](Method.GET, uri"/user/100")

        val program = (for {
            tnx <- transactor
            notFound <- ZIO.runtime[HasTransactor] >>= {_ =>
                HomeController(UserService.live(sys, tnx), sys).route.run(urlUser).value
            }
        } yield notFound).provideSomeLayer(transactorLayer)

        runtime.unsafeRun(program.fold(_ => (), println))
    }

    "Home controller create user" should "" in {
        import io.circe.syntax._
        import org.http4s.circe._
        import io.circe.generic.auto._
        import org.http4s.circe.CirceEntityDecoder._

        val urlUser = Request[Task](Method.POST, uri"/user")
          .withEntity{
              val userJson = User(None, Option("John")).asJson
              logger.debug(userJson.toString())
              userJson
          }

        val program = (for{
            tnx <- transactor
            create <- HomeController(UserService.live(sys, tnx), sys).route.run(urlUser)/*.map{c =>
                    c.as[Int]
                }*/.value
        } yield create).provideSomeLayer(transactorLayer)

        runtime.unsafeRun(program.fold(_ => (), println))
    }
}
