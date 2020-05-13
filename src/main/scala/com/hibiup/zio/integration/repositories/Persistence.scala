package com.hibiup.zio.integration.repositories


import akka.actor.ActorSystem
import cats.effect.{Blocker, Resource}
import com.hibiup.zio.integration.configuration.HasConfiguration
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import doobie.Transactor
import doobie.h2.H2Transactor
import zio.blocking.Blocking
import zio.{Managed, Reservation, Task, ZIO, ZLayer}
import zio.interop.catz._

import scala.concurrent.ExecutionContext

object Persistence extends StrictLogging{
    private def getTransactor(
                      conf: Config,
                      connectEC: ExecutionContext,
                      transactEC: ExecutionContext
                    ): Managed[Throwable, Transactor[Task]] = {
        /**
         * newH2Transactor 返回包含有 Transactor 的 (Cats.effect.) Resource.
         *
         * Transactor 内部维持了一个池，但是用于链接的 ec 和用于执行事务的 ec 也需要作为参数每次传入。
         */
        val resource: Resource[Task, Transactor[Task]] = H2Transactor.newH2Transactor[Task](
              conf.getString("url"),
              conf.getString("user"),
              conf.getString("password"),
              connectEC,
              Blocker.liftExecutionContext(transactEC)   // 在 Blocker 中运行该资源的分配，这个 Blocker 将使用传入的 EC
        )

        /**
         * resource.allocated 返回一对 Tuple，第一个元素是分配出的资源；第二个元素是释放资源的方法.
         *
         * 通过 map 映射成 ZIO 的 Reservation 以便传递给 ZIO 的 Managed。
         */
        val reservation: ZIO[Any, Throwable, Reservation[Any, Nothing, Transactor[Task]]] = resource.allocated.map{
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
        }.uninterruptible

        // Put Reservation into Managed
        Managed(reservation)
    }

    /**
     * 从环境中取得 Config 用于构建 Transactor 然后它 暴露成 ZLayer
     *
     * 这个过程中需要用到 Blocking 池。因此它对环境的要求是两个 Layers (Configuration 和 Blocking) 的结合
     * 如果不希望将某个 Layer，比如 Blocking 暴露给用户，可以使用 .provideSomeLayer 来设置，这样就只需要暴露出一个依赖 Layer，
     * 而不是用 with 链接的两个。
     */
    import com.hibiup.zio.integration.configuration.Configuration.DSL._
    def live(implicit connectEC: ExecutionContext): ZLayer[HasConfiguration with Blocking, Throwable, HasTransactor] = {
        ZLayer.fromManaged{
            for {
                // 其实直接调用 ZIO.descriptor.map(_.executor.asEC) 来取得 ExecutionContext，之所以用 zio.blocking.blocking
                // 是个因为这个过程本身可能也会用时较长。
                blockingEC <- zio.blocking.blocking(
                    ZIO.descriptor.map(_.executor.asEC)
                ).toManaged_
                // ZIO ==> ZManaged，必须将 ZIO 变成 ZManaged, 否则会形成 ZIO[ZManaged...]] 嵌套，导致 ZLayer.from...无所适从
                tnx <- load.toManaged_
                  .flatMap((conf: Config) =>
                    getTransactor(conf.getConfig("db.source"), connectEC, blockingEC)  // ZManaged
                )
            } yield tnx
        }
    }

    object DSL {
        def transactor: ZIO[HasTransactor, Throwable, Transactor[Task]] = {
            ZIO.access(_.get)
        }
    }
}
