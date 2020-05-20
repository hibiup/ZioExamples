package com.hibiup.zio.akka.repositories

import doobie.free.connection.ConnectionIO
import zio.{Has, Layer, Task, ZIO, ZLayer}
import doobie.implicits._

object UserRepository {
    trait Service[T[_]]{
        def create(task:User):T[ConnectionIO[TaskId]]
    }

    object Service extends Service[Task] {
        override def create(task:User): Task[ConnectionIO[TaskId]] = Task.effect {
            task match {
                case User(None, name) =>
                    sql"""
                          INSERT INTO User(name)
                          VALUES (
                              $name
                          )
                     """.update.withUniqueGeneratedKeys[Int]("id")

                case _ => throw new RuntimeException("Invalid input")
            }
        }
    }

    val live: Layer[Throwable, HasTaskRepository] = ZLayer.succeed(Service)

    object DSL {
        def create(user:User):ZIO[HasTaskRepository, Throwable, ConnectionIO[TaskId]] = ZIO.accessM(_.get.create(user))
    }
}
