package com.hibiup.zio

import com.typesafe.scalalogging.StrictLogging
import org.scalatest.FlatSpec
import zio.{Exit, IO, Task, UIO, ZIO}

class Example_1_Effect extends FlatSpec with StrictLogging{
    "Basic effective functions" should "" in {
        /**
         * succeed 函数是一个pure（eager 函数），直接得到返回值。
         */
        ZIO.succeed(42)
        // 同样适用其他衍生类型
        Task.succeed[Int](42)  // Task[A] = ZIO[Any, Throwable, A]

        /**
         * 相对于 eager 函数，effect 则是 lazy 的。例如对于存在副作用的标准输入：
         */
        import scala.io.StdIn
        val getStrLn: Task[Unit] = ZIO.effect(StdIn.readLine())

        /**
         *  effect 允许返回异常[Throwable, A]，如果保证不会出现异常，则适用 effectTotal 返回 [Nothing, A]
         */
        lazy val bigList = (0 to 1000000).toList   // 注意用 lazy suspend side effect
        lazy val bigString = bigList.map(_.toString).mkString("\n")
        val a: UIO[String] = ZIO.effectTotal(bigString)

        /**
         * 失败函数的也类似
         */
        ZIO.fail(new Exception("Uh oh!"))
        ZIO.fail(56)

        /**
         * ZIO 可以从 Scala 基本类型中获得值
         *
         * 注意到因为 Option 可能返回 None，这个值被视为某种"异常"，因此ZIO 的第二个参数位是 Unit，表示没有返回值的情况。
         */
        val zOption: ZIO[Any, Unit, Int] = ZIO.fromOption(Some(2))

        /**
         * 对于 fromFuture. ZIO 提供缺省 ec。
         */
        import scala.concurrent.Future
        lazy val future = Future.successful("Hello!")  // 注意用 lazy suspend side effect
        val zFuture: Task[Boolean] = ZIO.fromFuture { implicit ec =>
            future.map(_ => true)
        }

        // 以上等价于：
        val zFuture1: Task[Boolean] = ZIO.effect(Future.successful("Hello!")).flatMap(f => ZIO.fromFuture{ implicit ec =>
            f.map(_ => true)
        })

        // 或 (不使用 flatMap 更直观地表达):
        val zFuture2: Task[Boolean] = ZIO.effectSuspend{  // Suspend 等价于 map + flatten
            val f = Future.successful("Hello!")
            ZIO.fromFuture(implicit ec => f.map(_ => true))
        }

        /**
         * 对于第二个参数不是 Nothing 的数据类型，都存在 mapError 函数，map 到其他类型。
         */
        val zFutureStrErr: ZIO[Any, String, Boolean] = zFuture.mapError(_ => "None value found")

        /**
         * fromXXX 系列函数甚至可以作用于函数
         */
        val zFunc = ZIO.fromFunction((i: Int) => i * i)

        /**
         * Async
         */
        type User = String
        type AuthError = Throwable
        object legacy {
            def login(onSuccess: User => Unit, onFailure: AuthError => Unit): Unit = ???
        }

        val login: IO[AuthError, User] =
            IO.effectAsync[AuthError, User] { callback =>
                legacy.login(
                    user => callback(IO.succeed(user)),
                    err  => callback(IO.fail(err))
                )
            }
    }
}
