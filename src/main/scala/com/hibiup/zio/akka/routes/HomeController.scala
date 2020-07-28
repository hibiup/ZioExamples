package com.hibiup.zio.akka.routes

import akka.http.scaladsl.server.Directives._
import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.{Marshal, Marshaller, PredefinedToResponseMarshallers}
import akka.http.scaladsl.model.HttpHeader.ParsingResult.Ok
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, ResponseEntity, StatusCodes}
import akka.http.scaladsl.server.Directives.{complete, get, onSuccess, pathPrefix}
import akka.http.scaladsl.server.RouteResult.Complete
import akka.http.scaladsl.server.{Directives, Route, RouteResult, StandardRoute}
import com.hibiup.zio.akka.repositories.{HasTransactor, User, UserId}
import com.hibiup.zio.akka.services.HasUserService
import zio.{IO, Layer, Task, ZIO, ZLayer}

import scala.concurrent.{ExecutionContextExecutor, Future, Promise}

object HomeController{
    trait Service[T[_]] {
        def apply()(implicit serverLayer: Layer[Throwable, HasUserService], system:ActorSystem): T[Route]
    }

    object Service extends HomeController.Service[Task] with MarshallingSupport {
        import com.hibiup.zio.akka.services.UserService.DSL._

        /** Circe unmarshaller，用于处理 http request 输入值 */
        import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
        import io.circe.generic.auto._
        /** Circe marshaller，用于处理 http response 输出值 */
        import io.circe.syntax._

        override def apply()(implicit serviceLayer: Layer[Throwable, HasUserService], system:ActorSystem): Task[Route] = Task.effect{
            /**
             * 每个（get/post/delete/put) directive 返回 IO[A, StandardRoute]（StandardRoute 是 complete 函数的返回类型），
             * 然后由 MarshallingSupport.standardRouteToRoute 隐式方法转换成 Route
             */
            get {
                pathPrefix("user" / IntNumber) { id =>
                    find(id).provideLayer(serviceLayer).foldM(
                        (t: Throwable) => Task.fail(t),
                        user => Task.succeed(user)
                    ).map{
                        case user:User => complete(HttpResponse(
                            StatusCodes.OK,
                            entity = HttpEntity(user.asJson.toString()))
                        )
                        case _ => complete(
                            HttpResponse(StatusCodes.NotFound)
                        )
                    }
                }
            } ~
            post {
                entity(Directives.as[User]) { user =>    /** 需要 circe unmarshaller 库 */
                    create(user).provideLayer(serviceLayer).foldM(
                        (t: Throwable) => Task.fail(t),
                        userId => Task.succeed(userId)
                    ).map{
                        case userId:UserId => complete(HttpResponse(
                            StatusCodes.Created,
                            entity = HttpEntity(userId.asJson.toString()))   /** 需要 circe marshaller 库 */
                        )
                        case _ => complete(
                            HttpResponse(StatusCodes.NotFound)
                        )
                    }
                }
            }
        }
    }

    def live: Layer[Throwable, HasHomeController] = ZLayer.succeed(Service)

    object DSL {
        def homeRoute(implicit serviceLayer: Layer[Throwable, HasUserService],
                      system:ActorSystem):ZIO[HasHomeController, Throwable, Route] = ZIO.accessM(_.get.apply())
    }
}
