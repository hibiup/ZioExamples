package com.hibiup.zio.integration.repositories

import cats.effect.{Blocker, ExitCode, Resource}
import doobie.h2.H2Transactor
import doobie.util.ExecutionContexts
import org.scalatest.flatspec.AnyFlatSpec
import zio.Task
import zio.interop.catz._
import doobie.implicits._

class TestH2Transactor extends AnyFlatSpec{
    "H2 Transactor" should "has pool internally" in {
            // Resource yielding a transactor configured with a bounded connect EC and an unbounded
            // transaction EC. Everything will be closed and shut down cleanly after use.
            val transactor: Resource[Task, H2Transactor[Task]] =
            for {
                ce <- ExecutionContexts.fixedThreadPool[Task](32) // our connect EC
                be <- Blocker[Task]    // our blocking EC
                xa <- H2Transactor.newH2Transactor[Task](
                    "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", // connect URL
                    "sa",                                   // username
                    "",                                     // password
                    ce,                                     // await connection here
                    be                                      // execute JDBC operations here
                )
            } yield xa

            def run =
                transactor.use { xa =>
                    for {
                        n1 <- sql"select 42".query[Int].unique.transact(xa)
                        n2 <- sql"select 43".query[Int].unique.transact(xa)
                        _ <- Task(println(n1, n2))
                    } yield ExitCode.Success
                }

            zio.Runtime.default.unsafeRun(run)
        }
}
