package com.hibiup.zio.examples

import java.io.IOException
import zio.console.{Console, getStrLn, putStrLn}
import zio.{App, ExitCode, ZIO}

/**
 * 程序从 zio.App 中继承出
 */
object HelloWorld extends App{
    /**
     * run 方法是 zio App 的入口方法。
     *
     * ZIO 容器类型被称为 zio 的 unexceptional（正常）值。
     *
     * ZIO 的参数第一个代表执行容器，第二个表示异常类型（不是必须为 Throwable），第三个是正常返回值类型。根据 ZIO 的三个参数的不同
     * 情况，ZIO 定义了几种别名：
     *
     *   UIO[A]     -  ZIO[Any, Nothing, A]
     *   URIO[R, A] -  ZIO[R, Nothing, A]
     *   Task[A]    - ZIO[Any, Throwable, A]
     *   RIO[R, A]  - ZIO[R, Throwable, A]
     *   IO[E, A]   - ZIO[Any, E, A]
     *
     * 这些别名各自存在伴随对象，包含一些特定的函数。
     *
     * 和 Scala 的 Either 或 Option 的 fold 一样，ZIO通过 fold 来同时处理处理异常和正常发挥值。
     *
     * run 函数作为入口函数，它要求返回 Int 类型返回值（对应 ExitCode）, 不允许存在异常。
     *Example_1_Effect
     * 这个例子的 run 在调用 myAppLogic 后得到一个 ZIO 返回值传递给 fold，无论什么异常，最终都返回 1，否则返回 0。
     */
    def run(args: List[String]): ZIO[Console, Nothing, ExitCode] = myAppLogic.exitCode  //.fold(_ => 1, _ => 0)

    /**
     * putStrLn/putStr 是 console 提供给的标准打印函数。getStrLn 是标准输入设备的读取函数。
     *
     * 同时它们也是 Monad，也就意味着可以串联： `getStrLn flatMap putStrLn` 能够将标准输入直接打印到标准输出。
     */
    val myAppLogic: ZIO[Console, IOException, Unit] = for {
        _    <- putStrLn("Hello! What is your name?")   // 标准输入输出返回的执行容器是 Console
        name <- getStrLn
        _    <- putStrLn(s"Hello, ${name}, welcome to ZIO!")
    } yield ()
}
