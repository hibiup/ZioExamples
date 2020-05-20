package com.hibiup.zio.akka

import akka.actor.ActorSystem
import zio.Has

package object config {
    type HasConfiguration = Has[Configuration.Service]
    type HasActorSystem = Has[ActorSystem]
}
