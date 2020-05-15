package com.hibiup.zio.integration.configuration

import akka.actor.ActorSystem
import com.typesafe.scalalogging.StrictLogging
import zio.{Has, Managed, Task, ZIO, ZLayer}

object AkkaActorSystem extends StrictLogging{
    val live: ZLayer[Any, Throwable, HasActorSystem] = ZLayer.fromManaged{
        /**
         * 比较一下 Managed 在 Transactor 中的用法。
         */
        Managed.make{
            Task.effect {
                logger.info("ActorSystem acquisition")
                ActorSystem("my-system")
            }
        }{
            sys => Task.fromFuture(_ => {
                logger.info("ActorSystem release")
                sys.terminate
            }).either
        }
    }

    object DSL {
        def actorSystem:ZIO[Has[ActorSystem], Throwable, ActorSystem] =
            ZIO.access(s => s.get)
    }
}
