package com.hibiup.zio.examples

import com.typesafe.scalalogging.StrictLogging
import org.scalatest.flatspec.AnyFlatSpec
import zio.ZIO
import zio.console._
import zio.Runtime.global

class Example_2_Environment extends AnyFlatSpec with StrictLogging{
    val runtime = global

    /**
     * 所谓 Environment，也就是 ZIO 的第一个类型参数。
     *
     * ZIO 就像 Monad (sequential composition), ApplicativeError (typed errors) 和 Reader (environment).
     * 的混合体，它的 Environment 可以是容器，也可以是环境变量。并且它们可以被 compose 成新的对像，这种组合甚至支持类型推断。
     *
     * access 方法提供了对环境的访问，相当于 Reader 的功能。
     */
    "Environment" should "be composible" in {
        // ZIO 访问 environment 的两个关键方法是 access 和 provide，environment 只是 access 的语法糖。
        // 不同的类型可以组合成新的"混合"类型，并且被自动推导出来，这个类型推导的过程是是静态的，因此它对运行时的速度没有影响。
        val a: ZIO[Console with Int, Nothing, Int] = for {
            env <- ZIO.environment[Int]   // = access[Int](identity): ZIO[Int, Nothing, Int]
            _   <- putStrLn(s"The value of the environment is: $env")  // ZIO[Console, Nothing, Unit]
        } yield {
            // println(s"The value of the environment is: $env")
            env
        }

        //val result = a.provide(Console.Live ++ 42)
        //runtime.unsafeRun(result)
    }

    "Environment" should "be accessible" in {
        final case class Config(server: String, port: Int)

        val configString: ZIO[Config, Nothing, String] = for {
            /**
             * ZIO.accessM[Dependency1](_.functionality1) 是一个帮助工具，用于访问 environment
             */
            server <- ZIO.access[Config](_.server)
            port   <- ZIO.access[Config](_.port)
        } yield s"Server: $server, port: $port"

        /**
         * provide 用于注入 environment
         */
        val result = configString.provide(Config("localhost", 8080))

        runtime.unsafeRun(result.map(println(_)))   // s"Server: localhost, port: 8080"
    }
}
