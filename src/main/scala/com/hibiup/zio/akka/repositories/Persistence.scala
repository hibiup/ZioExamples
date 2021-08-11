package com.hibiup.zio.akka.repositories

import cats.effect.{Blocker, Resource}
import com.hibiup.zio.akka.config.HasConfiguration
import zio.{Managed, _}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import com.zaxxer.hikari.HikariConfig
import doobie.Transactor
import doobie.h2.H2Transactor
import doobie.hikari.HikariTransactor
import zio.blocking.Blocking
import zio.interop.catz._

import scala.concurrent.ExecutionContext

object Persistence extends StrictLogging{
    private def getTransactor(
                               conf: Config,
                               connectEC: ExecutionContext,
                               transactEC: ExecutionContext): RManaged[Blocking, Transactor[Task]] =
        ZIO.runtime[Blocking].toManaged_.flatMap { implicit rt =>
            val config = new HikariConfig()
            config.setJdbcUrl(conf.getString("url"))
            config.setUsername(conf.getString("user"))
            config.setPassword(conf.getString("password"))
            //config.setSchema(conf.getString("")))

            HikariTransactor.fromHikariConfig[Task](
                config,
                connectEC,
                Blocker.liftExecutionContext(transactEC)
            ).toManaged
        }

    /*private def getTransactor(
                               conf: Config,
                               connectEC: ExecutionContext,
                               transactEC: ExecutionContext
                             ): Managed[Throwable, Transactor[Task]] = {
        val resource: Resource[Task, Transactor[Task]] = H2Transactor.newH2Transactor[Task](
            conf.getString("url"),
            conf.getString("user"),
            conf.getString("password"),
            connectEC,
            Blocker.liftExecutionContext(transactEC)
        )

        val reservation: ZIO[Any, Throwable, Reservation[Any, Nothing, Transactor[Task]]] = resource.allocated.map{
            case (transactor, cleanupM) => {
                Reservation(
                    {
                        logger.debug("Transactor acquisition")
                        ZIO.succeed(transactor)
                    },
                    _ => {
                        logger.debug("Transactor release")
                        cleanupM.orDie
                    })
            }
        }.uninterruptible

        Managed(reservation)
    }*/

    import com.hibiup.zio.akka.config.Configuration.DSL._
    def live(implicit connectEC: ExecutionContext): ZLayer[HasConfiguration with Blocking, Throwable, HasTransactor] =
        ZLayer.fromManaged{
            for {
                blockingEC <- zio.blocking.blocking {
                    ZIO.descriptor.map(_.executor.asEC)
                }.toManaged_

                tnx <- load.toManaged_
                  .flatMap((conf: Config) =>
                      getTransactor(conf.getConfig("db.source"), connectEC, blockingEC)
                  )
            } yield tnx
        }

    object DSL {
        def transactor: ZIO[HasTransactor, Throwable, Transactor[Task]] = {
            ZIO.access(_.get)
        }
    }
}
