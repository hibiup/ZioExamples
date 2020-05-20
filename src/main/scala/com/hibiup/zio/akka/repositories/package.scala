package com.hibiup.zio.akka

import java.sql.Timestamp

import zio.{Has, Task}
import doobie.Transactor

package object repositories {
    type TaskId = Int
    type HasTransactor = Has[Transactor[Task]]
    type HasTaskRepository = Has[UserRepository.Service[Task]]

    type UserId = Int
    sealed trait Entity{
        val id: Option[Int]
    }

    final case class User(id:Option[UserId], name:Option[String]) extends Entity
}
