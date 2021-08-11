package com.hibiup.zio.examples

import zio.{App, ExitCode, Task, ZIO}

class HowToExample1 extends App{
    final case class UserProfile(id:Long, name:String)

    /**
     * 定义接口
     */
    object Database {
        /**
         * Interface
         */
        trait Service {
            def lookup(id: Long): Task[UserProfile]
            def update(id: Long, profile: UserProfile): Task[Unit]
        }

        /**
         * (可选实现一些帮助方法) 这些帮助方法并非必须，但是可以提供更友好的 DSL 界面
         */
        object DSL {
            def lookup(id: Long): ZIO[Database, Throwable, UserProfile] =
            // 通过 ZIO.access 来访问环境可以获得诸多好处，比如类型的组合。
                ZIO.accessM(_.database.lookup(id))

            def update(id: Long, profile: UserProfile): ZIO[Database, Throwable, Unit] =
                ZIO.accessM(_.database.update(id, profile))
        }
    }

    trait Database {
        def database: Database.Service
    }

    /**
     * 实现接口
     */
    trait DatabaseLive extends Database {
        def database: Database.Service =
            new Database.Service {
                def lookup(id: Long): Task[UserProfile] = Task{
                    println("lookup: " + id)
                    UserProfile(id, "Anonymous")
                }
                def update(id: Long, profile: UserProfile): Task[Unit] =Task{
                    println("update: " + profile)
                }
            }
    }

    object DatabaseLive extends DatabaseLive

    override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, ExitCode] = {
        import Database.DSL._
        val lookedupProfile: ZIO[Database, Throwable, UserProfile] =
            for {
                profile <- lookup(10)
                _ <- update(10, profile)
            } yield profile

        lookedupProfile.provide(DatabaseLive).exitCode  //.fold(_ => 1, _ => 0)
    }
}
