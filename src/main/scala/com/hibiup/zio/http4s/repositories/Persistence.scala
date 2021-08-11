package com.hibiup.zio.http4s.repositories

import cats.effect.{Blocker, Resource}
import com.hibiup.zio.http4s.configuration.HasConfiguration
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import com.zaxxer.hikari.HikariConfig
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import zio.blocking.Blocking
import zio.{Task, ZIO, ZLayer, ZManaged}
import zio.interop.catz._

import scala.concurrent.ExecutionContext

object Persistence extends StrictLogging{
    /**
     * 定义一个用于管理连接池的线程池。
     */
    val fixedSizeConnectionPool: ZManaged[Any, Throwable, ExecutionContext] =
        ExecutionContexts.fixedThreadPool[Task](5).toManagedZIO


    private def getTransactor(
                               conf: Config,
                               connectEC: ExecutionContext,
                               transactEC: ExecutionContext): ZManaged[Any, Throwable, HikariTransactor[Task]] =
        //ZIO.runtime[Blocking].toManaged_.flatMap { implicit rt =>
        {
            val config = new HikariConfig()
            config.setJdbcUrl(conf.getString("url"))
            config.setUsername(conf.getString("user"))
            config.setPassword(conf.getString("password"))
            //config.setSchema(conf.getString("")))

            /**
             * Transactor.fromConfig 返回 获取资源的句柄和 和 release 句柄，在这里风别是 HikariTransactor[TasK] 和 Task[Unit]
             */
            val resource: Resource[Task, HikariTransactor[Task]] = HikariTransactor.fromHikariConfig[Task](
                config,
                connectEC,
                Blocker.liftExecutionContext(transactEC)
            )

            /*val reservation: ZIO[Any, Throwable, Reservation[Any, Nothing, HikariTransactor[Task]]] = resource.allocated.map{
                // 转换： Cats.Resource ==> zio.Reservation
                case (transactor, cleanupM) => {
                    logger.debug("Reservation")
                    /**
                     * Reservation 接受两个ZIO类型的参数，一个是资源的获取，一个是资源的销毁。Reservation 并不指定资源何时被使用。
                     */
                    Reservation(
                        {
                            logger.debug("Reservation acquisition")
                            ZIO.succeed(transactor)
                        },
                        _ => {
                            logger.debug("Reservation revoke")
                            cleanupM.orDie
                        }
                    )
                }
            }.uninterruptible*/

            // Put Reservation into Managed
            //Managed(reservation)

            // 转换： Cats.Resource ==> zio.Reservation
            ZManaged.fromEffect(resource.allocated.map{
                case (transactor: HikariTransactor[Task], releaseM: Task[Unit]) =>
                    ZManaged.make{
                        logger.debug("Reservation acquisition")
                        ZIO.succeed(transactor)
                    }{ _ =>
                        logger.debug("Reservation revoke")
                        releaseM.orDie
                    }
                }.uninterruptible
            ).flatten
        }

    /**
     * 从环境中取得 Config 用于构建 Transactor 然后它 暴露成 ZLayer
     *
     * 这个过程中需要用到 Blocking 池。因此它对环境的要求是两个 Layers (Configuration 和 Blocking) 的结合
     * 如果不希望将某个 Layer，比如 Blocking 暴露给用户，可以使用 .provideSomeLayer 来设置，这样就只需要暴露出一个依赖 Layer，
     * 而不是用 with 链接的两个。
     */
    import com.hibiup.zio.http4s.configuration.Configuration.DSL._
    def live/*(implicit connectEC: ExecutionContext)*/: ZLayer[HasConfiguration with Blocking, Throwable, HasTransactor] = {
        ZLayer.fromManaged{
            for {
                connectEC <- fixedSizeConnectionPool
                /**
                 * 获得用于执行事务的线程池，它和管理连接的线程池可以不是同一个。
                 *
                 * 可以直接调用 ZIO.descriptor.map(_.executor.asEC) 来取得 ExecutionContext，之所以用 zio.blocking.blocking
                 * 是个因为这个过程本身可能也会用时较长。
                 * */
                blockingEC <- zio.blocking.blocking {
                    /**
                     * Doobie 缺省提供了一个的用于执行的事务的 EC。
                     */
                    ZIO.descriptor.map(_ => doobie.util.ExecutionContexts.synchronous)
                    //ZIO.descriptor.map(_.executor.asEC)   // 如果希望直接使用当前 Fiber 的 EC（不推荐）
                }.toManaged_
                // ZIO ==> ZManaged，必须将 ZIO 变成 ZManaged, 否则会形成 ZIO[ZManaged...]] 嵌套，导致 ZLayer.from...无所适从
                tnx <- load.toManaged_
                  .flatMap((conf: Config) =>
                    getTransactor(conf.getConfig("db.source"), connectEC, blockingEC)  // ZManaged
                )
            } yield tnx
        }
    }

    object DSL {
        def transactor: ZIO[HasTransactor, Throwable, HikariTransactor[Task]] = {
            ZIO.access(_.get)
        }
    }
}
