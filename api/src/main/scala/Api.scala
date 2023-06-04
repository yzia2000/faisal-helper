package faisalHelper.api

import faisalHelper.shared.*
import zio.*
import zio.http.*
import zio.json.*

object Api {

  import Endpoints.*

  // TODO: persist emails scheduled in a database so that user
  // can see which emails were sent in the past and which ones weren't.
  val app: App[Queue[SendEmailDto]] =
    Http.collectZIO[Request] {
      case req @ Method.POST -> !! / Endpoints.Email.sendEmail =>
        for {
          input <- req.body.asString
            .flatMap(data => ZIO.fromEither(data.fromJson[SendEmailDto]))
            .mapError(err =>
              Response.text(s"Failed to decode body json ${err}")
            )
          _ <- GmailEmailSender
            .queueEmail(input)
            .mapError(err => Response.text(s"Failed to send emails ${err}"))
          res <- ZIO
            .succeed(Response.text("Success"))
        } yield res
      case Method.GET -> !! / "hello" =>
        ZIO.succeed(Response.text("Hello world!"))
    }
}
