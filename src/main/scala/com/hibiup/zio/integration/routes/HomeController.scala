package com.hibiup.zio.integration.routes

import akka.actor.ActorSystem
import com.hibiup.zio.integration.repositories.{HasUserService, User, UserId, UserService}
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

    def apply(layers: Layer[Throwable, HasUserService])(implicit actorSystem:ActorSystem): HomeController = new HomeController {
        override def route: HttpRoutes[Task] = {
            import DSL._

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
         * DSL 不是 ZIO environment 规范必须的，只是为用户提供一个友好的访问服务的 helper，在这我们可以决定依赖的注入的时机：
         *
         *  1）如果我们希望控制依赖的注入，那么可以调用 provideLayer 来注入依赖，这时候对于使用者来说返回的是 Task[ReturnType]。
         *
         *  2）或者也可以将依赖的注入交给用户来决定，那么返回类型为 ZIO[Has[TypeOfService, _, ReturnType]。（参见 UserService.DSL）
         */
        object DSL{
            import UserService.DSL._
            import com.hibiup.zio.integration.configuration.AkkaActorSystem.DSL._

            /**
             * 在 dsl 方法内部植入 Layer，这样接口直接返回 Task，并且不需要用户环境提供这个 Layer
             */
            def getUser(id:UserId):Task[User] = for{
                user <- find(id).provideLayer(layers)
            } yield user

            def createUser(user:User): Task[UserId] = for{
                userId <- create(user).provideLayer(layers)
            } yield userId
        }
    }
}


