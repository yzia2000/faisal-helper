package faisalHelper.api

import faisalHelper.shared.*
import jakarta.mail.*
import jakarta.mail.internet.{InternetAddress, MimeMessage}
import zio.*
import zio.http.*
import zio.http.HttpAppMiddleware.*
import zio.json.*
import zio.stream.*

import java.util.Properties

object Main extends ZIOAppDefault {
  def run: ZIO[Any, Throwable, Nothing] = (Server
    .serve(Api.app @@ cors() @@ requestLogging())
    .fork *> GmailEmailSender.emailProcessor *> ZIO.never)
    .provide(
      GmailEmailSender.queueLayer,
      Server.defaultWithPort(sys.env.getOrElse("PORT", "8080").toInt)
    )
}
