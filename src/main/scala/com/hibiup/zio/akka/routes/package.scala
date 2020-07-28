package com.hibiup.zio.akka

import zio.{Has, Task}

package object routes {
    type HasHomeController = Has[HomeController.Service[Task]]
}
