package faisalHelper.api

import faisalHelper.shared.*
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import zio.*
import zio.http.HttpAppMiddleware.*
import zio.http.*
import zio.json.*
import zio.stream.*

import java.util.Properties

object EmailGenerator {
  def fillPlaceHolders(input: GeneratorInput, template: String) =
    // TODO: add feature to allow users to dynamically set tokens
    template
      .replace("${name}", input.name)
      .replace("${company}", input.company)

  def generate(templateInput: TemplateInput)(input: GeneratorInput): Email = {
    val body = fillPlaceHolders(input, templateInput.bodyTemplate)
    val subject = fillPlaceHolders(input, templateInput.subjectTemplate)

    Email(input.email, subject, body)
  }
}

object GmailEmailSender {
  def sendEmail(session: Session)(email: Email) = {
    ZIO.attemptBlocking {
      val message = new MimeMessage(session)
      message.setRecipients(
        Message.RecipientType.TO,
        email.to
      )
      message.setSubject(email.subject)
      message.setText(email.body)

      Transport.send(message)
    }.logError
      *> ZIO.logInfo(s"Successfully sent email to ${email.to}")
  }

  def processEmails(input: SendEmailDto): ZIO[Any, Throwable, Unit] = {
    for {
      _ <- ZIO.unit
      session = makeSession(input.auth)
      emails = input.recipients
        .map(EmailGenerator.generate(input.templateInput))
      _ <- ZIO.foreach(emails)(x => sendEmail(session)(x).ignore)
    } yield ()
  }

  def queueEmail(
      emails: SendEmailDto
  ): ZIO[Queue[SendEmailDto], Throwable, Unit] = {
    for {
      queue <- ZIO.service[Queue[SendEmailDto]]
      _ <- queue.offer(emails)
    } yield ()
  }

  def makeSession(auth: Auth): Session = {
    // For now we hard code gmail config here. Migration plan:
    // 1. Move this to run properties
    // 2. Make this generic (not just gmail but also other providers)
    val props = new java.util.Properties()
    props.put("mail.smtp.auth", "true")
    props.put("mail.smtp.starttls.enable", "true")
    props.put("mail.smtp.host", "smtp.gmail.com")
    props.put("mail.smtp.port", "587")

    Session.getInstance(
      props,
      new Authenticator {
        override def getPasswordAuthentication: PasswordAuthentication = {
          new PasswordAuthentication(
            auth.email,
            auth.password
          )
        }
      }
    )
  }

  def emailProcessor = for {
    queue <- ZIO.service[Queue[SendEmailDto]]
    // TODO: add retry support
    _ <- ZStream
      .fromQueue(queue)
      .foreach(processEmails)
  } yield ()

  def queueLayer = ZLayer.fromZIO(Queue.bounded[SendEmailDto](10))
}

object Api {
  import Endpoints._

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

object Main extends ZIOAppDefault {
  def run = (Server
    .serve(Api.app @@ cors() @@ requestLogging())
    .fork *> GmailEmailSender.emailProcessor *> ZIO.never)
    .provide(
      GmailEmailSender.queueLayer,
      Server.defaultWithPort(sys.env.get("PORT").getOrElse("8080").toInt)
    )
}
