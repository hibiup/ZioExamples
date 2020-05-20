package com.hibiup.zio.akka

import zio.{Has, Task}

package object services {
    type HasTaskService = Has[UserService.Service[Task]]
}
