package com.hibiup.zio.http4s

import akka.actor.ActorSystem
import zio.{Has, RIO}

package object routes {
    type HasActorSystem = Has[ActorSystem]
}
