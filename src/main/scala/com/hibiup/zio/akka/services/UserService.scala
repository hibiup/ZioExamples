package com.hibiup.zio.akka.services

import com.hibiup.zio.akka.repositories.{HasTransactor, User, UserId, UserNotFound, UserRepository}
import com.typesafe.scalalogging.StrictLogging
import doobie.Transactor
import zio.{Layer, Task, ZIO, ZLayer}
import doobie.implicits._

object UserService extends StrictLogging{
    import zio.interop.catz.taskConcurrentInstance

    trait Service[T[_]] {
        def find(id: UserId): T[User]
        def create(user: User): T[Int]
        def remove(id: UserId): T[Boolean]
    }

    private def apply(tnx: Transactor[Task]): UserService.Service[Task] = {
        import com.hibiup.zio.akka.repositories.UserRepository.DSL._

        logger.info("Create UserService.Service")
        new Service[Task] {
            override def find(id: UserId): Task[User] = {
                for{
                    u <- select(id).provideLayer(UserRepository.live) >>= { user =>
                        user.flatMap(_.option
                          .transact(tnx)
                          .foldM(
                              err => Task.fail(err),
                              maybeUser => Task.require(UserNotFound(id))(Task.succeed(maybeUser))
                          ))
                    }
                } yield u
            }

            override def create(user: User): Task[UserId] = {
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

            override def remove(id: UserId): Task[Boolean] =
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

    def live(implicit tnx:Transactor[Task]): Layer[Throwable, HasUserService] = {
        ZLayer.succeed{
            // 因为 UserService 需要通过ZLayer层来获得依赖的 Transactor，因此也只能在 ZLayer 层创建。
            UserService(tnx)
        }
    }

    object DSL {
        def create(user:User): ZIO[HasUserService, Throwable, Int] =
            ZIO.accessM(f => f.get.create(user))
        def find(id:UserId): ZIO[HasUserService, Throwable, User] =
            ZIO.accessM(f => f.get.find(id))
        def remove(id: UserId): ZIO[HasUserService, Throwable, Boolean] =
            ZIO.accessM(f => f.get.remove(id))
    }
}
