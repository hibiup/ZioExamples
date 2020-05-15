package com.hibiup.zio.integration.repositories

import com.typesafe.scalalogging.StrictLogging
import doobie.free.connection.ConnectionIO
import doobie.util.query.Query0
import doobie.util.update.Update0
import doobie.implicits._
import zio.{Has, Layer, Task, ZIO, ZLayer}

object UserRepository extends StrictLogging{
    trait Service{
        def select(id: UserId): Query0[User]
        def insert(user: User): ConnectionIO[Int]
        def delete(id: UserId): Update0
    }

    object Service extends Service{
        def select(id: UserId): Query0[User] = {
            logger.info("select user")
            sql"""SELECT * FROM USERS WHERE id = ${id} """.query[User]
        }

        def insert(user: User): ConnectionIO[Int] = {
            logger.info("insert user")
            sql"""INSERT INTO USERS (name) VALUES ( ${user.name.get})""".update.withUniqueGeneratedKeys[Int]("id")
        }

        def delete(id: UserId): Update0 = {
            logger.info("delete user")
            sql"""DELETE FROM USERS WHERE id = ${id}""".update
        }
    }

    val live: Layer[Throwable, Has[Service]] = ZLayer.succeed(Service)

    object DSL {
        def select(id: UserId): ZIO[HasUserRepository,Throwable, Task[Query0[User]]] =
            ZIO.access(f => Task.effect(f.get.select(id)))
        def insert(user: User): ZIO[HasUserRepository,Throwable, Task[ConnectionIO[Int] ]] =
            ZIO.access(f => Task.effect(f.get.insert(user)))
        def delete(id: UserId): ZIO[HasUserRepository,Throwable, Task[Update0]] =
            ZIO.access(f => Task.effect(f.get.delete(id)))
    }
}
