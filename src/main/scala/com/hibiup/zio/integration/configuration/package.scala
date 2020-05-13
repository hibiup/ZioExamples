package com.hibiup.zio.integration

package object configuration {
    type HasConfiguration = zio.Has[Configuration.Service]
}
