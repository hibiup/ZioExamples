package com.hibiup.zio.http4s.routes

import akka.actor.ActorSystem
import com.hibiup.zio.http4s.repositories.{HasUserService, User, UserId, UserService, UserServiceTask}
import com.typesafe.scalalogging.StrictLogging
import zio._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import zio.interop.catz._


trait HomeController {
    def route: HttpRoutes[Task]
}

object HomeController extends StrictLogging{
    val dsl = new Http4sDsl[Task]{}
    import dsl._

    import io.circe.syntax._
    import org.http4s.circe._
    import io.circe.generic.auto._
    import org.http4s.circe.CirceEntityDecoder._

    def apply(implicit layers: Layer[Throwable, HasUserService], actorSystem:ActorSystem): HomeController = new HomeController {
        override def route: HttpRoutes[Task] = {

            HttpRoutes.of[Task] {
                case _@GET -> Root / "user" / IntVar(id) =>
                    getUser(id:UserId).foldM(_ => NotFound(), (u: User) => Ok(u.asJson))

                case request@POST -> Root / "user" => {
                    for {
                        user <- request.as[User]
                        resp <- createUser(user:User).foldM(
                              t => {
                                  logger.error(t.getMessage, t)
                                  InternalServerError()
                              },
                              (userId: UserId) => Created(userId.asJson)
                          )
                    } yield resp
                }

                case _@DELETE -> Root / IntVar(id) => ???
                    //(get(id) *> deleteUser(id)).foldM(_ => NotFound(), Ok(_))
            }
        }

        /**
         * 业务方法
         */
        import UserService.DSL._

        def getUser(id:UserId): Task[User] =
            find(id).provideLayer(layers)   // 注入依赖

        def createUser(user:User):Task[UserId] =
            create(user).provideLayer(layers)
    }
}


