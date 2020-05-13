package com.hibiup.zio.integration

import zio.{Has, Task}
import doobie.Transactor

package object repositories {
    type UserId = Int

    type UserServiceTask = UserService.Service[Task]
    type HasUserService = Has[UserServiceTask]
    type HasUserRepository = Has[UserRepository.Service]

    type HasTransactor = Has[Transactor[Task]]

    sealed trait Entity{
        val id: Option[UserId]
    }

    final case class User(id:Option[UserId], name:Option[String]) extends Entity

    sealed trait RepoError
    final case class UserNotFound(id:UserId) extends Exception with RepoError {
        val message = s"User id $id has not been found"
    }
}
