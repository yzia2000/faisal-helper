package faisalHelper.api

import faisalHelper.shared.*
import zio.*
import zio.http.*
import zio.json.*

object Api {

  import Endpoints.*

  // TODO: persist emails scheduled in a database so that user
  // can see which emails were sent in the past and which ones weren't.
  val app: App[Queue[SendEmailDto] & Client] =
    Http.collectZIO[Request] {
      case req @ Method.GET -> Root / Endpoints.Authentication.google =>
        for {
          input <- ZIO
            .fromOption(req.header(Header.Authorization).collect {
              case Header.Authorization.Bearer(token) => token
            })
            .mapError(err =>
              Response
                .text(s"Please provide token in body $err")
                .withStatus(Status.BadRequest)
            )
          userInfo <- GoogleAuth
            .verify(input)
            .mapError(err =>
              Response
                .text(s"$err")
                .withStatus(Status.BadRequest)
            )
        } yield Response.json(userInfo.toJson)

      case req @ Method.POST -> Root / Endpoints.Email.sendEmail =>
        for {
          input <- req.body.asString
            .flatMap(data => ZIO.fromEither(data.fromJson[SendEmailDto]))
            .mapError(err =>
              Response
                .text(s"Failed to decode body json ${err}")
                .withStatus(Status.BadRequest)
            )
          _ <- GmailEmailSender
            .queueEmail(input)
            .mapError(err =>
              Response
                .text(s"Failed to send emails ${err}")
                .withStatus(Status.InternalServerError)
            )
          res <- ZIO
            .succeed(Response.text("Success"))
        } yield res
      case Method.GET -> Root / "hello" =>
        ZIO.succeed(Response.text("Hello world!"))
    }
}
