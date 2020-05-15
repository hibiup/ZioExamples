package com.hibiup.zio.integration

import akka.actor.ActorSystem
import zio.Has

package object configuration {
    type HasConfiguration = zio.Has[Configuration.Service]
    type HasActorSystem = Has[ActorSystem]
}
