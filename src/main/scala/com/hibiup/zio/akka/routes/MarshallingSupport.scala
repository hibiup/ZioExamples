package com.hibiup.zio.akka.routes

import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.RouteResult.Complete
import akka.http.scaladsl.server.{Route, RouteResult, StandardRoute}
import com.hibiup.zio.akka.repositories.UserNotFound
import zio.internal.Platform
import zio.{IO, Runtime}

import scala.concurrent.{Future, Promise}

trait MarshallingSupport extends Runtime[Unit] { self =>
    override val environment: Unit  =  Runtime.default.environment // environment type Unit,
    override val platform: Platform = zio.internal.Platform.default

    sealed trait ErrorToHttpResponse[E] {
        def toHttpResponse(value: E): HttpResponse
    }

    private def generateHttpResponseFromError(error: Throwable) : HttpResponse = {
        error match {
            case e@UserNotFound(_) =>
                HttpResponse(StatusCodes.NotFound, entity = HttpEntity(e.message))
            case e@_ =>
                HttpResponse(StatusCodes.NotFound, entity = HttpEntity(e.getMessage))
        }
    }

    implicit def errorHttp: ErrorToHttpResponse[Throwable] = new ErrorToHttpResponse[Throwable] {
        override def toHttpResponse(value: Throwable): HttpResponse = {
            generateHttpResponseFromError(value)
        }
    }

    implicit val errorMarshaller: Marshaller[Throwable, HttpResponse] = {
        Marshaller { implicit ec => error   =>
            val response = generateHttpResponseFromError(error)
            PredefinedToResponseMarshallers.fromResponse(response)
        }
    }

    /**
     * 如果上下文中存在 akka (un)marshaller 参数，比如基于 Spray.json 的 json marshaller，可以直接让 Route 返回 ZIO。
     * 在本例中因为使用 circe，所以这个隐式没有用到。
     */
    implicit def ioEffectToMarshallable[E, A](implicit m1: Marshaller[A, HttpResponse], m2: Marshaller[E, HttpResponse]): Marshaller[IO[E, A], HttpResponse] = {
        //Factory method for creating marshallers
        Marshaller { implicit ec =>
            effect =>
                val promise = Promise[List[Marshalling[HttpResponse]]]()
                val marshalledEffect: IO[Throwable, List[Marshalling[HttpResponse]]] = effect.foldM(
                    err => IO.fromFuture(_ => m2(err)),
                    suc => IO.fromFuture(_ => m1(suc))
                )
                self.unsafeRunAsync(marshalledEffect) { done =>
                    done.fold(
                        failed => promise.failure(failed.squash),
                        success => promise.success(success)
                    )
                }
                promise.future
        }
    }

    /**
     * 这个隐式将由 complete 函数返回的 StandardRoute 转化成 Route。
     */
    implicit def standardRouteToRoute[E](effect: IO[E, StandardRoute])(implicit errToHttp: ErrorToHttpResponse[E]): Route = {
        //type Route = RequestContext ⇒ Future[RouteResult]
        ctx =>
            val promise = Promise[RouteResult]()

            val foldedEffect = effect.fold(
                err => { Future.successful(Complete(errToHttp.toHttpResponse(err))) },
                suc => suc.apply(ctx)
            )

            self.unsafeRunAsync(foldedEffect) { done =>
                done.fold(
                    err => promise.failure(err.squash),
                    suc => promise.completeWith(suc)
                )
            }

            promise.future
    }
}