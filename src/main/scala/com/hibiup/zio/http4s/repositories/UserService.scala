package com.hibiup.zio.http4s.repositories

import akka.actor.ActorSystem
import com.typesafe.scalalogging.StrictLogging
import doobie.Transactor
import zio.{Layer, Task, ZIO, ZLayer}
import doobie.implicits._

object UserService extends StrictLogging{
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
                     * 调用 UserRepository.DSL，从环境中获得 UserRepository，同时注入 UserRepository，因为 UserRepository 是
                     * 业务（UserService）内部的依赖，所以我们没有将它暴露成接口，而是在使用的时候才注入。
                     *
                     * 相比于直接使用 UserRepository，通过 ZLayer（DSL）取得的好处是解藕两个模块之间的依赖。ZLayer 定义的是
                     * 接口信息，然后通过 access 方法来查询实现，而 provideLayer 负责注入实现。要注意区别这两者的生命周期。
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

    /**
     * 从 environment (由 MainEntry 注入)中取得 Transactor 和 Akka 构建出 UserService。
     *
     * 我们没有在 ZLayer 上要求注入 UserRepository，因为基于哪些 Repository 来执行业务由 UserService 内部决定。UserService
     * 对外只要求依赖 Transactor。
     *
     * 问题：每次对依赖方法的访问都会通过查询 Layer 来得到，这因此导致不断触发 UserService 的创建。需要考虑一个方法来减少这个过程。
     */
    def live(implicit actorSystem:ActorSystem, tnx:Transactor[Task]): Layer[Throwable, HasUserService] = {
        ZLayer.succeed{
            // 因为 UserService 需要通过ZLayer层来获得依赖的 Transactor，因此也只能在 ZLayer 层创建。
            UserService(tnx)
        }
    }

    /**
     * DSL 不是 ZIO environment 规范必须的，只是为用户提供一个友好的访问依赖的界面，并且在这我们可以决定依赖的注入时机：
     *
     * 1）如果我们希望隐藏依赖，那么可以在这里调用 provideLayer，这时候对于使用者来说返回的是 Task[ReturnType]。
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
