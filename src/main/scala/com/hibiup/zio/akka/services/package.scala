package com.hibiup.zio.akka

import zio.{Has, Task}

package object services {
    type HasUserService = Has[UserService.Service[Task]]
}
