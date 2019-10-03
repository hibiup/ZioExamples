package com.hibiup.zio

import com.typesafe.scalalogging.StrictLogging
import org.scalatest.FlatSpec
import zio.{App, DefaultRuntime, IO, Task, UIO, ZIO}

class Example_1_Effect extends FlatSpec with StrictLogging{
    val runtime = new DefaultRuntime {}

    "Basic effective functions" should "" in {
        /**
         * succeed 函数是一个pure（eager 函数），直接计算出参数值。
         *
         * ZIO 函数执行后的到一个 ZIO[_,_,_] 类型的返回值，称为 `effect`. 有两种方法拆包。
         *
         *   App：参考 HelloWorld
         *   runtime: 通过 Runtime 来拆包
         */
        val succ = ZIO.succeed(42)
        runtime.unsafeRun(succ.map(v => {
            logger.info(s"$v")
            assert(v === 42)
        }))

        // 同样适用其他衍生类型
        val tSucc = Task.succeed[Int](41)  // Task[A] = ZIO[Any, Throwable, A]
        runtime.unsafeRun(tSucc.map(v => assert(v === 41)))

        /**
         * 相对于 eager 函数，effect 则是 lazy 的。例如对于存在副作用的标准输入：
         */
        import scala.io.StdIn
        val getStrLn: Task[Unit] = ZIO.effect(StdIn.readLine())

        /**
         *  effect 允许返回异常[Throwable, A]，如果保证不会出现异常，则适用 effectTotal 返回 [Nothing, A]
         */
        lazy val bigList = (0 to 100).toList   // 注意用 lazy suspend side effect
        lazy val bigString = bigList.map(_.toString).mkString("\n")
        val a: UIO[String] = ZIO.effectTotal(bigString)
        runtime.unsafeRun(a.map(s => logger.info(s"$s")))

        /**
         * 失败函数的也类似. mapError 可以改变错误类型，但是不能修改错误状态: zio.FiberFailure
         */
        try runtime.unsafeRun(ZIO.fail(new Exception("Uh oh!")).mapError(t => println(t.getMessage)))
        catch{
            case t:zio.FiberFailure => logger.info(t.getMessage, t)
        }

        try runtime.unsafeRun(ZIO.fail(56))
        catch {
            case t:zio.FiberFailure => succeed
        }

        /**
         *  但是可以通过 either 来封装错误，并改变错误状态:
         *
         *      ZIO[R, E, A] => ZIO[R, Nothing, Either[E, A]]
         */
        val zeither: UIO[Either[String, Int]] = IO.fail("Uh oh!").either
        runtime.unsafeRun(zeither.map{
            case Right(_) => fail()
            case Left(v) =>
                logger.info(s"$v")
                assert(v === "Uh oh!")
        })

        /**
         * 于 either 相反的运算是 absolve
         */
        val zabsolve: ZIO[Any, String, Nothing] = IO.succeed(Left("Absolve!")).absolve
        try runtime.unsafeRun(zabsolve)
        catch {
            case t:zio.FiberFailure => logger.info(t.getMessage(), t)
        }

        /**
         * ZIO 可以从 Scala 基本类型中获得值
         *
         * 注意到因为 Option 可能返回 None，这个值被视为某种"异常"，因此ZIO 的第二个参数位是 Unit，表示没有返回值的情况。
         */
        val zOption: ZIO[Any, Unit, Int] = ZIO.fromOption(Some(2))
        runtime.unsafeRun(zOption.map(v => assert(v === 2)))

        /**
         * 对于 fromFuture. ZIO 提供缺省 ec。
         */
        import scala.concurrent.Future
        lazy val future = Future.successful("Hello!")    // 注意用 lazy suspend side effect
        val zFuture: Task[Boolean] = ZIO.fromFuture { implicit ec =>
            future.map(_ => true)
        }
        runtime.unsafeRun(zFuture.map(b => {
            logger.info(s"$b")
            assert(b)
        }))

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
         * fromXXX 系列函数甚至可以作用于函数.
         *
         * 通过 provide 函数将参数传入
         */
        val zFunc = ZIO.fromFunction((i: (Int, Int)) => i._1 * i._2)
        runtime.unsafeRun(zFunc.provide((2,3)).map(v => logger.info(s"$v")))

        /**
         * Async
         */
        type User = String
        type AuthError = Throwable
        object legacy {
            def login(onSuccess: User => Unit, onFailure: AuthError => Unit): Unit = ???
        }

        val login: IO[AuthError, User] = IO.effectAsync[AuthError, User] { callback =>
            legacy.login(
                user => callback(IO.succeed(user)),
                err  => callback(IO.fail(err))
            )
        }

        /**
         * Blocking effect. 可以被转换成 ZIO
         * */
        import zio.blocking._
        val sleeping: ZIO[Blocking, Throwable, Unit] = effectBlocking(Thread.sleep(Long.MaxValue))

        // 或允许 cancel 的 blocking effect
        def cancelableSleeping(obj:Object): ZIO[Blocking, Throwable, Unit] = {
            /**
             * Cancelable blocking 接受两个函数参数，第二个函数是 cancel 的方法。
             */
            effectBlockingCancelable{obj.wait()}{UIO.effectTotal(obj.notify())}
        }
    }
}
