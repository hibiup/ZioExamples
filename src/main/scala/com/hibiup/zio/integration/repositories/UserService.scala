package com.hibiup.zio.integration.repositories

import akka.actor.ActorSystem
import com.typesafe.scalalogging.StrictLogging
import doobie.Transactor
import zio.{Task, ZIO, ZLayer}
import doobie.implicits._

object UserService extends StrictLogging{
    //import UserRepository._
    import zio.interop.catz.taskConcurrentInstance   // requested by transact

    trait Service[T[_]] {
        def find(id: UserId): T[User]
        def create(user: User): T[Int]
        def remove(id: UserId): T[Boolean]
    }

    private def apply(tnx: Transactor[Task]): UserServiceTask = {
        import UserRepository.DSL._

        logger.info("Create UserService.Service")
        new Service[Task] {
            override def find(id: UserId): Task[User] = {
                for{
                    /**
                     * 调用 UserRepository.DSL 从环境中获得 UserRepository，同时注入 UserRepository，因为 UserRepository 是
                     * 业务（UserService）内部的依赖，所以我们没有将它暴露成接口，而是在使用的时候才注入。
                     *
                     * 实际上也可以直接使用 UserRepository，但是通过接口取得的好处是解藕两个模块之间的依赖。
                     * */
                    u <- select(id)
                      .provideLayer(UserRepository.live) >>= { user =>
                        user.flatMap(_.option
                          .transact(tnx)
                          .foldM(
                              err => Task.fail(err),
                              maybeUser => Task.require(UserNotFound(id))(Task.succeed(maybeUser))
                          ))
                    }
                } yield u
            }

            override def create(user: User): Task[Int] = {
                for{
                    u <- insert(user).provideLayer(UserRepository.live) >>= { userId =>
                        userId.flatMap(_.transact(tnx)
                          .foldM(
                              err => Task.fail(err),
                              Task.succeed(_)
                          ))
                    }
                } yield u
            }

            override def remove(id: UserId): Task[Boolean] = {
                for{
                    u <- delete(id).provideLayer(UserRepository.live) >>= { userId =>
                        userId.flatMap(_.run.transact(tnx)
                          .fold(
                              _ => false,
                              _ => true
                          ))
                    }
                } yield u
            }
        }
    }

    import Persistence.DSL._
    /**
     * 从 environment (由 UserController 传入)中取得 Transactor 构建出 UserService。
     *
     * 我们没有在 ZLayer 上要求注入 UserRepository，因为基于哪些 Repository 来执行业务由 UserService 内部决定。UserService
     * 对外只要求依赖 Transactor。
     *
     * 问题：因为 Transactor 不断地创建（因为它是 Managed），导致 UserService 也不断地创建。需要考虑一个方法来减少这个过程。
     */
    def live(implicit actorSystem:ActorSystem): ZLayer[HasTransactor, Throwable, HasUserService]  = {
        ZLayer.fromEffect {
            transactor.map(tnx => {
                UserService(tnx)
            })
        }
    }

    /**
     * DSL 不是 ZIO environment 规范必须的，只是为用户提供一个友好的访问依赖的界面，在这我们可以决定依赖的注入时机：
     *
     * 1）如果我们希望控制依赖的注入，那么可以调用 provideLayer 来注入依赖，这时候对于使用者来说返回的是 Task[ReturnType]。（参考 HomeController）
     *
     * 2) 或者也可以将依赖的注入交给用户来决定，那么返回类型为 ZIO[Has[TypeOfService, _, ReturnType]。（参见 UserService.Service
     *    中的业务方法）
     */
    object DSL {
        def create(user:User): ZIO[HasUserService, Throwable, Int] =
            ZIO.accessM(f => f.get.create(user))
        def find(id:UserId): ZIO[HasUserService, Throwable, User] =
            ZIO.accessM(_.get.find(id))
    }
}
