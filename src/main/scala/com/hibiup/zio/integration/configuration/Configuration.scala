package com.hibiup.zio.integration.configuration

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import zio.{Layer, Task, ZIO, ZLayer}

object Configuration extends StrictLogging{
    trait Service {
        val load: Task[Config]
    }

    private object Service extends Configuration.Service {
        val load: Task[Config] = Task.effect{
            logger.debug("Load config")
            ConfigFactory.load()
        }
    }

    val live: Layer[Throwable, HasConfiguration] = ZLayer.succeed{
        Service
    }

    object DSL {
        val load:ZIO[HasConfiguration, Throwable, Config] = ZIO.accessM(_.get.load)
    }
}