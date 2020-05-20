package com.hibiup.zio.akka.routes

import com.hibiup.zio.akka.repositories.User
import akka.actor.{Actor, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

// ToResponseMarshallable to circe json marshaller
import io.circe.syntax._
import io.circe.generic.auto._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

import scala.concurrent.Future

object HomeController {
    def routes(system:ActorSystem): Route = {
        get {
            pathPrefix("user" / LongNumber) { id =>
                val maybeItem: Future[Option[User]] = ???
                onSuccess(maybeItem) {
                    case Some(user) => ??? //complete(user.asJson)
                    case None => complete(StatusCodes.NotFound)
                }
            }
        }
    }
}
