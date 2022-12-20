package com.hibiup.zio.examples

import com.typesafe.scalalogging.StrictLogging
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.Assertion
import zio._
import zio.stream.ZStream

/**
 * ZIO 缺省的内部容器：
 */
class Example_1_Effect extends AnyFlatSpec with StrictLogging{
    /**
     * Runtime 是缺省的运行时，它提供了任务的上下文管理，比如线程池、协程切换等，如果我们通过继承 App 来新建我们的可执行换进，那么App为我们提
     * 供了一个缺省的 Runtime，或者可以手工创建一个：
     */
    val runtime = Runtime.default

    "Basic effective functions" should "" in {
        /**
         * succeed 用于一个不会出错的运算，返回一个用于执行不会抛出异常的错运算的 effect 容器.
         */
        val resultContainer: UIO[Int] = ZIO.succeed(42)

        /**
         * 将容器交给 Runtime 来执行，如果继承至 App 则不需要手工执行这一步。
         */
        runtime.unsafeRun(resultContainer.map(v => {
            logger.info(s"$v")
            assert(v === 42)
        }))

        /**
         *  对于一个不会失败的计算，可以生命为 UIO, 它相当于 ZIO[R, Nothing, A]
         */
        lazy val bigList = (0 to 100).toList   // 注意用 lazy suspend side effect
        lazy val bigString = bigList.map(_.toString).mkString("\n")
        val a: UIO[String] = UIO(bigString)
        val b: UIO[String] = ZIO.effectTotal(bigString)   // 等效
        val res: ZIO[Any, Nothing, Assertion] = for{
            aStr <- a
            bStr <- b
        } yield {
            println(aStr)
            println(bStr)
            assert(aStr == bStr)
        }

        runtime.unsafeRun(res)

        /**
         * Task 衍生至 ZIO，内部使用的缺省"环境"，Any，也就是说该运算对环境没有要求，只需要提供一个正确的返回值类型即可，错误的缺省类型是 Throwable。
         *
         * 如果对环境有要求，比如希望使用第三方容器，比如 Cats IO, 那么可以使用 RIO, 它允许我们指定一个执行容器，比如：RIO[cats.effect.IO, Int]
         */
        val tSucc: Task[Int] = Task.effect[Int](41)  // Task[A] = ZIO[Any, Throwable, A]
        runtime.unsafeRun(tSucc.map(v => assert(v === 41)))

        /**
         * fail 用于一个必定错误的运算，返回一个包含错误 zio.FiberFailure 的容器，可以通过 mapError 在返回之前重定向错误处理但是不能阻止抛出异常
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
         *  但是可以通过 either 来将错误容器映射回正常的 UIO 容器:
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
         * ZIO 可以从其它容器类型中获得结果，比如 Scala 的 Option
         *
         * 注意到因为 Option 可能返回 None，这个值被视为某种"异常"，因此ZIO 的第二个参数位是 Unit，表示没有返回值的情况。
         */
        val zOption: IO[Option[Nothing], Int] = ZIO.fromOption(Some(2))
        runtime.unsafeRun(zOption.map(v => assert(v === 2)))

        /**
         * 对于 fromFuture. ZIO 提供缺省 ec（来自 runtime）。
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
         * 实际上对于第二个参数不是 Nothing 的数据类型，都存在 mapError 函数，map 到其他类型。
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
         * effectAsync 返回一个"异步容器"
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
         * Blocking effect 为长时间阻塞运行的任务提供了一个额外的线程管理容器，以避免阻塞当前的任务。
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


    "Effect Async" should "" in {
        // Java Interface
        trait JavaInterface {
            def onEvent1(): Task[Unit]

            def onEvent2(msg: String): Task[Unit]

            def onError(t: Throwable): Task[Unit]
        }

        class Server() {
            var controlEvents: JavaInterface = _

            def setControlEvent(controlEvents: JavaInterface) = {
                this.controlEvents = controlEvents
            }
        }

        // ADT
        sealed trait Event
        final case class OnEvent1(run: () => Unit) extends Event
        final case class OnEvent2(run: String => Unit) extends Event
        final case class OnError(run: Throwable => Unit) extends Event

        // ZIO
        def setCallback(server: Server): Task[Unit] = Task {
            server.setControlEvent(new JavaInterface {
                // Effect async
                override def onEvent1(): Task[Unit] = /*runtime.unsafeRun*/ Task.effectAsync[Unit] { register =>
                    register {
                        ZIO.succeed(OnEvent1(() => {
                            println("Event 1")
                        }).run())
                    }
                }

                override def onEvent2(msg: String): Task[Unit] = /*runtime.unsafeRun*/ Task.effectAsync[Unit] { register =>
                    register {
                        ZIO.succeed(OnEvent2(_msg => println(_msg)).run(msg))
                    }
                }

                override def onError(t: Throwable): Task[Unit] = /*runtime.unsafeRun*/ Task.effectAsync[Unit] { register =>
                    register {
                        ZIO.succeed(OnError(_t => _t.printStackTrace()).run(t))
                    }
                }
            })
        }

        runtime.unsafeRun(
            (for {
                server <- Task(new Server)
                _ <- setCallback(server)
                _ <- server.controlEvents.onEvent1()
                _ <- server.controlEvents.onEvent2("Event 2")
            } yield server).map { s =>
                ()
                //s.controlEvents.onEvent1()
                //s.controlEvents.onEvent2("Event 2")
            }
        )


        // Death lock
        /*def setCallback1(server:Server):Task[Unit] = Task.effectAsync[Unit] { register: (ZIO[Any, Throwable, Unit] => Unit) =>
            server.setControlEvent( new JavaInterface {
                    // Effect async
                    override def onEvent1(): Task[Unit] = Task(
                        register {
                            ZIO.succeed(OnEvent1(() => ()).run)
                        }
                    )


                    override def onEvent2(msg: String): Task[Unit] = Task(
                        register {
                            ZIO.succeed(OnEvent2(_msg => println(_msg)).run(msg))
                        }
                    )

                    override def onError(t: Throwable): Task[Unit] = Task(
                        register {
                            ZIO.succeed(OnError(_t => _t.printStackTrace()).run(t))
                        }
                    )
                })
            }


        runtime.unsafeRun(
            (for{
                server <- Task(new Server)
                _ <- setCallback1(server)
                _ <- server.controlEvents.onEvent2("Event 2")
            } yield server).map( s =>
                () //s.controlEvents.onEvent2("Event 2")
            )
        )*/

        //runtime.unsafeRun(effect)
    }
}
