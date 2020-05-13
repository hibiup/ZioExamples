package com.hibiup.zio.examples

import zio.{Has, IO, Layer, Managed, RIO, Task, UIO, URIO, ZIO, ZLayer}

import scala.concurrent.Future


object HowToExample_Has extends App {
    final case class UserId(id:Long)
    final case class User(userId: UserId, name:String)
    class DBError(t:Throwable) extends Throwable

    /**
     * 1）定义一些服务接口
     */
    object Repo{
        trait Service{
            def getUser(userId: UserId): IO[DBError, Option[User]]
            def createUser(user: User): IO[DBError, Option[UserId]]
        }
    }

    object Logger{
        trait Service {
            def log(message:String):UIO[Unit]
        }
    }

    /**
     * 用 Has 来代理依赖的数据类型。
     */
    type Repo = Has[Repo.Service]
    type Logger = Has[Logger.Service]

    // 获得接口的实例
    val repo: Repo = Has(new Repo.Service{
        override def getUser(userId: UserId): IO[DBError, Option[User]] = IO{
            println("getUser")
            Option(User(userId, "John"))
        }.mapError(e => new DBError(e))

        override def createUser(user: User): IO[DBError, Option[UserId]] = IO{
            println("createUser")
            Option(UserId(10))
        }.mapError(e => new DBError(e))
    })

    val logger: Logger = Has((message: String) => UIO(println(message)))

    // Has 提供了一些便于组合的运算，比如 `++`，可以将抗个 Has 组合在一起，这也是为什么我们用 Has 来代理这些数据类型。
    val mix: Repo with Logger = repo ++ logger

    /**
     * 如果我们要使用其中的数据类型：
     */
    val log = mix.get[Logger.Service].log("Hello modules!")  // IDE 可能会误报某个隐式找不到

    zio.Runtime.default.unsafeRun(log)
}

/**
 * 不过一般并不直接使用 Has 来管理依赖，而是使用 ZLayer
 */
object HowToExample_ZLayer extends App {
    import zio.duration._

    final case class UserId(id:Long)
    final case class User(userId: UserId, name:String)
    final class DBError(t:Throwable) extends Throwable

    type Repo = Has[UserRepository.Service]

    /**
     * 1）定义一些服务接口
     */
    object UserRepository {
        trait Service {
            def getUser(userId: UserId): IO[DBError, Option[User]]
            def createUser(user: User): IO[DBError, Option[UserId]]
            def close():Task[Unit]
        }

        /**
         * 2) 用 ZLayer 来管理依赖"层"，返回 Has
         *
         * fromManaged 用于定义一个"接受受管制"的对像，用 Managed,make 来生成，接受两个 Lambda 参数，分别是使用前动作和使用后动作。
         * 第一个参数的类型是 IO[E, A]
         * 第二个参数的类型是 UIO[Any]
         */
        val zlayer: ZLayer[Any, Throwable, Repo] = ZLayer.fromManaged(
            Managed.make{
                Task.effect(
                    new UserRepository.Service {
                        override def getUser(userId: UserId): IO[DBError, Option[User]] = IO {
                            println("getUser")
                            Option(User(userId, "John"))
                        }.mapError(e => new DBError(e))

                        override def createUser(user: User): IO[DBError, Option[UserId]] = IO {
                            println("createUser")
                            Option(UserId(10))
                        }.mapError(e => new DBError(e))

                        override def close(): Task[Unit] = Task/*.fromFuture*/{//implicit ec =>
                            //Future(println("closed"))
                            println("closed")
                        }
                    }
                )
            }(repo => repo.close().either)
        )

        /**
         * 3）DSL, 这不是必须的，只是方便对方法的使用
         */
        object DSL {
            //accessor methods
            def getUser(userId: UserId): ZIO[Repo, DBError, Option[User]] =
                ZIO.accessM(_.get.getUser(userId))

            def createUser(user: User): ZIO[Repo, DBError, Option[UserId]]  =
                ZIO.accessM(_.get.createUser(user))
        }
    }

    /**
     * 2-1) 定义另一个服务接口
     */

    type Logging = Has[Logger.Service[UIO]]

    import zio.console.Console
    object Logger{
        trait Service[T[_]] {
            def info(s: String): T[Unit]
            def error(s: String): T[Unit]
        }

        /**
         * 伴随对像
         */
        object Service{
            def apply(console: Console): Service[UIO] = new Service[UIO] {
                def info(s: String): UIO[Unit]  = console.get.putStrLn(s"info - $s")
                def error(s: String): UIO[Unit] = console.get.putStrLn(s"error - $s")
            }
        }

        /**
         * 2-2）得到 zlayer
         *
         *   fromFunction 将依赖环境以参数形式传入
         */
        val zlayer: ZLayer[Console, Nothing, Logging] = ZLayer.fromFunction( (console:Console) =>
            Service(console)
        )

        /**
         * 2-3）包装访问 zlayer 的 DSL
         */
        object DSL {
            def info(s: String): ZIO[Logging, Nothing, Unit] =
                ZIO.accessM(_.get.info(s))

            def error(s: String): ZIO[Logging, Nothing, Unit] =
                ZIO.accessM(_.get.error(s))
        }
    }

    /**
     * 4) 可以组合
     */

    // 获得接口的实例
    val userRepoZLayer: ZLayer[Any, Throwable, Repo] = UserRepository.zlayer
    val loggerZLayer: ZLayer[Console, Nothing, Logging] = Logger.zlayer

    // Has 提供了一些便于组合的运算，比如 `++`，可以将抗个 Has 组合在一起，这也是为什么我们用 Has 来代理这些数据类型。
    val mixZLayer = userRepoZLayer ++ loggerZLayer

    /**
     * 5）
     */
    import UserRepository.DSL._
    import Logger.DSL._
    val user2: User = User(UserId(123), "Tommy")
    val makeUser: ZIO[Logging with Repo, DBError, Unit] = for {
        i <- info(s"inserting user")    // ZIO[Logging, Nothing, Unit]
        uid <- createUser(user2)          // ZIO[UserRepo, DBError, Unit]
        _ <- info(s"user inserted: $uid")     // ZIO[Logging, Nothing, Unit]
    } yield ()

    /**
     * 6)
     */
    zio.Runtime.default.unsafeRun(makeUser.provideLayer(mixZLayer).fold(_ => 1, _ => 0))
}