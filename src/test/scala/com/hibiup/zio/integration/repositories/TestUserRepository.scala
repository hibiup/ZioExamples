package com.hibiup.zio.integration.repositories

import akka.actor.ActorSystem
import com.hibiup.zio.integration.configuration.Configuration
import org.scalatest.flatspec.AnyFlatSpec
import zio.blocking.Blocking
import com.typesafe.scalalogging.StrictLogging

class TestUserRepository extends AnyFlatSpec with StrictLogging{
    "Persistent with Configuration" should "" in {
        import zio._

        val runtime = zio.Runtime.default
        import UserService.DSL._

        implicit val sys = ActorSystem("test-actorSystem")

        val program: ZIO[HasUserService, Throwable, User] = for{
            created <- {
                logger.debug("program")
                create(User(None, Option("Jhon")))
            }
            found <- find(created)
        } yield found


        /**
         * ++ 相当于 with，得到一个新的混合 layer
         * >>> 是将 Layer 作为参数传给另外一个 Layer，以允许接送者通过 ZIO.accessM 访问到注入的 Layer
         */
        val layers = (Configuration.live ++  Blocking.live) >>>
          Persistence.live(runtime.platform.executor.asEC) >>>
          UserService.live(sys)

        runtime.unsafeRun(program.provideSomeLayer(layers).map(u => {
            logger.info(u.toString)
        })  )
    }
}
