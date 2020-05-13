package com.hibiup.zio.examples

import zio.URIO

object MonadPatten extends App {
    /**
     * 1）定义箱子
     *
     * 所谓定义一个额箱子，也就是将"待执行函数"作为参数装入一个"容器"中，然后通过调用容器的 flatMap 和 map 来触发"待执行函数"。
     * flatMap 和 map 是箱子的执行函数，它们的参数装入的"待执行函数"的返回值，然后将结果再次装箱返回。
     * 在获得"待执行函数"返回值的时候也就触发了"待执行函数"的执行。
     * */
    class IO[+A](val unsafeInterpret: () => A) {
        // (第一次执行时) f 是 `_ => getStrLn`
        def flatMap[B](f: A => IO[B]): IO[B] = {
            // 为 f 的 body 传入参数（ unsafeInterpret()的返回值 ），然后调用传入的 body（执行 getStrLn）
            //
            // 执行 unsafeInterpret 也就是执行了第一次构造该类的时候装箱的内容： println("Good morning, what's your name?")。
            // 得到 A:Unit。这个输入并没有什么卵用，因此在定义 f 的 body 的时候用下划线忽略掉了。
            //
            // f 的返回值是 => getStrLn 执行的结果 IO[String]，满足 flatMap 参数类型的要求。
            f(this.unsafeInterpret())
        }

        // Functor
        def map[B](f: A => B): IO[B] =
            IO.pure(f(this.unsafeInterpret()))
    }

    /**
     * 2）定义一个装箱函数
     *
     * 一个将"待执行函数"装入箱子的函数
     * */
    object IO {
        def pure[A](eff: => A) = new IO(() => eff)
    }

    /**
     * 3）调用装箱函数
     *
     * 调用装箱函数也就是将"待执行函数"装入箱子，然后将箱子返回。这个过程在传统编程中也就是直接执行"待执行函数"，但是在 FP 中，这个过程
     * 被推迟到箱体的 flatMap 或 map 被调用时才执行。
     */
    def putStrLn(line: String): IO[Unit] = IO.pure(
        println(line)
    )

    val getStrLn: IO[String] = IO.pure(
        scala.io.StdIn.readLine()
    )

    /**
     * 4）将箱体串联起来。
     */
    val program: IO[Unit] = for {
        _    <- putStrLn("Good morning, what's your name?")
        name <- getStrLn
        _    <- putStrLn(s"Great to meet you, $name")
    } yield ()

    /*val program: IO[Unit] = putStrLn("Good morning, what's your name?")
      .flatMap(_ => getStrLn)  // 传入 f 的 body (getStrLn)
      .flatMap(name => putStrLn(s"Great to meet you, $name"))*/

    /**
     * 5）触发箱体连锁执行。
     */
    program.unsafeInterpret()
}


object ReaderMonadPatten extends App {
    /**
     * Reader monad 通过 identity 将环境 T 的签名转换成 T => T，以便于 compose 待调用的函数。
     */
    case class Reader[-R, +A](provide: R => A) { self =>
        def flatMap[R1 <: R, B](f: A => Reader[R1, B]): Reader[R1, B] = {
            /**
             * 6）真实的 compose 过程（生成时）和执行调用点（使用时）。以下过程具有两个时态，函数体是在生成时生成的，这时候的一切参数
             * 都参考生成时的值，而最后使用这个容器的时候是使用时，传入的参数是最终的环境变量。
             */
            Reader[R1, B](  // 第三个，也是最后返回的 Reader 实例
                // 使用时：r 是在最后一行调用 provide 的时候传入的 Config
                (r: R1) => {
                    /**
                     * 生成时：
                     *
                     * 生成一个用于打包输入的 Config 的 Reader 实例，此时的 self.provide 是 identity 函数，它将参数 Config
                     * 转换成 R => R 的形式付给 f 函数。f 函数是第 3 步传入的 Reader.point 函数，它将返回值装箱。
                     */
                    val a: Reader[R1, B] = f(
                        // `self` 是第一个 Reader 实例：
                        // 此时的 provide 是之前执行 Reader.access -> Reader.environment 的时候传入的 identity 函数，它将 Config
                        // 换成 R => R 的形式交付给 f 函数去打包，结果保存在下一个新的 Reader 实例中。
                        self.provide(r)    // identity(Config) => Config。
                    )
                    // a 是第二个 Reader 实例：
                    // 它是一个包含了"待执行函数" `a => a.serverName` 的 Reader。这个 Reader 由 point 函数生成，它的 provide
                    // 不是 `identity`，而是"待执行函数" `a => a.serverName`。
                    a.provide(r)  // 将 Config 传递给 `a => a.serverName`，将这个作为 "待执行函数" 固化在新的最后一个 Reader 实例中。
                }
            )
        }

        def map[B](f: A => B): Reader[R, B] = {
            /**
             * 5）执行环境和"待执行函数"的 compose
             *
             * f 是"待调用函数"，它的值是: `a => a.serverName`; a 则来自 flatMap 的输出，也就是 Config
             * 因此以下执行的是 a: Config => Reader(a => a.serverName) 得到一个新的包含 `a => a.serverName` 待执行函数的 Reader 实例。
             */
            flatMap((a: A) => Reader.point(f(a)))
        }
    }

    object Reader {
        def point[A](a: => A): Reader[Any, A] = {
            Reader(_ => a)  // pure
        }

        def environment[R]: Reader[R, R] = {
            /**
             * 3）identity[A](a:A):A 函数的签名形式是：`T => T`, 满足 Reader 的参数形式。生成一个具有 Reader[T, T] 签名的实例。
             *
             * 也就是说这个 Reader 实例内部保存了一个忠实反应输入参数的函数，通过这个identity，我们可以将一个 T 签名参数转成 T => T 签名。
             * 以满足 Reader 的参数格式，这样这个 Reader 就可以用来和具有"待执行函数"的 Reader 进行 compose。
             */
            Reader(identity)    // 传入"生成时"参数，得到 Reader(identity(_))， provide := identity
        }
        def access[R, A](f: R => A): Reader[R, A] = {
            /**
             * 2）access 的作用是将"环境"和"待执行函数" compose 在一起。所谓"环境"其实是包含获取输入值的函数（identity）的容器。
             */
            val e = environment[R]
             /**
              * 4）保存有 identity 函数的 Reader, compose "待执行函数"：`a => a.serverName`
              */
            e.map(f)
        }
    }

    case class Config(serverName: String, port: Int)

    /**
     * 1）生成绑定有"环境"的"待执行函数"的容器。
     */
    val serverName: Reader[Config, String] =
        Reader.access[Config, String](a => a.serverName) // 传入"生成时"参数，返回 Reader(provide = a => a.serviceName)

    /**
     * 7）执行第 6 步返回的 Reader 实例的 provide，也就是第 6 步中定义的过程。
     */
    val name = serverName.provide(Config("localhost", 43))

    println(name)

    // 正如之前所见, point 将任意类型的"环境值"一个值存入 Reader[Any, A]，比如字符串：
    val tempFile: Reader[Any, String] = Reader.point("/tmp/tempfile.dat")
    val file = tempFile.provide(())
    println(file)
}
