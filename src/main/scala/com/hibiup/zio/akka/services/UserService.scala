package com.hibiup.zio.akka.services

import com.hibiup.zio.akka.repositories.{User, HasTransactor, TaskId}
import com.typesafe.scalalogging.StrictLogging
import doobie.util.transactor.Transactor
import zio.{Task, ZIO, ZLayer}

object UserService extends StrictLogging{
    trait Service[T[_]] {
        def newTask(user:User)(implicit tnx:Transactor[Task]):T[TaskId]
    }

    def live: ZLayer[HasTransactor, Throwable, HasTaskService] = ZLayer.fromEffect{
        import com.hibiup.zio.akka.repositories.Persistence.DSL._
        transactor.map{ implicit tnx =>
            object Service extends Service[Task] {
                override def newTask(user: User)(implicit tnx:Transactor[Task]): Task[TaskId] = {
                    ???
                }
            }
            Service
        }
    }
}
