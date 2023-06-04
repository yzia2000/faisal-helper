package faisalHelper.api

import faisalHelper.shared.*
import zio.*
import zio.http.*
import zio.http.HttpAppMiddleware.*

object Main extends ZIOAppDefault {
  def run: ZIO[Any, Throwable, Nothing] = (Server
    .serve(
      Api.app @@ cors() @@ requestLogging(
        logResponseBody = true
      )
    )
    .fork *> GmailEmailSender.emailProcessor *> ZIO.never)
    .provide(
      GmailEmailSender.queueLayer,
      Server.defaultWith(config =>
        config
          .port(sys.env.getOrElse("PORT", "8080").toInt)
      )
    )
}
