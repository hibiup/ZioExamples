package com.hibiup.zio.integration

import akka.actor.ActorSystem
import zio.{Has, RIO}

package object routes {
    type HasActorSystem = Has[ActorSystem]
}
