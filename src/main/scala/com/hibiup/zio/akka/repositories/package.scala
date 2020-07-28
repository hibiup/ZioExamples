package com.hibiup.zio.akka

import java.sql.Timestamp

import zio.{Has, Task}
import doobie.Transactor

package object repositories {
    type HasTransactor = Has[Transactor[Task]]
    type HasUserRepository = Has[UserRepository.Service]

    type UserId = Int
    sealed trait Entity{
        val id: Option[Int]
    }

    final case class User(id:Option[UserId], name:Option[String]) extends Entity

    sealed trait RepoError
    final case class UserNotFound(id:UserId) extends Exception with RepoError {
        val message = s"User id $id has not been found"
    }
}
