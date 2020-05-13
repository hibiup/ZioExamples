package com.hibiup.zio.integration

import cats.effect.ExitCode
import com.hibiup.zio.integration.configuration.{AkkaActorSystem, Configuration}
import com.hibiup.zio.integration.repositories.{HasUserService, Persistence, UserService}
import com.hibiup.zio.integration.routes.{HasActorSystem, HomeController}
import com.typesafe.scalalogging.StrictLogging
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import zio.blocking.Blocking
import zio._
import zio.clock.Clock
import zio.interop.catz._

import scala.concurrent.ExecutionContext

object MainEntry extends zio.App with StrictLogging{
    import AkkaActorSystem.DSL._

    override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] = {
        for{
            sys <- actorSystem  // get ActorSystem
            server <- ZIO.runtime[Clock with HasActorSystem] >>= { implicit rt =>
                implicit val ec: ExecutionContext = rt.platform.executor.asEC
                import org.http4s.implicits._

                /**
                 * Persistence.live 使用到 actorSystem，但是不能以 ZLayer 的形式合并在一起。否则在每次请求服务的时候都会触发
                 * ActorSystem 的 acquisition 和 release。
                 *
                 * provideLayer 从 ZLayer 中取得目标 Service，如果 ZLayer 是 Managed，那么"分配"和"释放"（lambda）函数也会
                 * 被记录下来，当我们通过 access（ZIO(map.get[Service]))）返回注入的服务，使用前后"分配"和"释放"函数都会被执行。
                 * 因此如果以 ZLayer 的方式传入 ActorSystem，然后使用会造成不断的新建和释放。
                 */
                implicit val actorSystem = sys

                /**
                 * ++: 合并两个 layer 相当于 with
                 * >>>: 将 Layer1 传递给另一个layer2，这样就可以在 layer2 中用 ZIO.access[Has[Layer1]] 来取得。
                 *
                 * UserService 需要获得 Transactor 来控制事务的范围，但是并不希望将 UserRepository 暴露出来，因此 UserService
                 * 的 DSL 返回的签名要求 HasTransactor 而不要求 UserRepository，并且我们也需要注入 Persistent(实际上是 Transactor)。
                 * 而 UserService 在自己的业务方法内部注入了 UserRepository(参见 UserService 的业务方法)。
                 */
                val layers: Layer[Throwable, HasUserService] =
                    (Configuration.live ++ Blocking.live) >>> Persistence.live(ec) >>> UserService.live(sys)

                val httpApp = Router[Task](
                    "/" -> HomeController(layers)(sys).route
                ).orNotFound

                import zio.interop.catz.implicits._
                BlazeServerBuilder[Task](ec)
                  .bindHttp(9000, "0.0.0.0")
                  .enableHttp2(true)
                  .withHttpApp(httpApp)
                  .serve
                  .compile[Task, Task, ExitCode]
                  .drain
            }
        } yield server
        /**
         * 注意 ActorSystem 载入的时机，因为它是全局的，并且具有 acquisition/release 两个动作，因此需要确保它在启动的时候被载入
         * 服务销毁的时候被释放，而不是伴随每个请求而动作。而 Transactor 则是伴随每次请求而动作的。
         */
    }.provideSomeLayer(Clock.live ++ AkkaActorSystem.live).fold(_ => 1, _ => 0)
}