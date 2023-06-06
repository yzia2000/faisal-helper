package faisalHelper.api

import com.google.api.client.extensions.java6.auth.oauth2.*
import com.google.api.client.googleapis.auth.oauth2.*
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.Base64
import com.google.api.services.gmail.*
import com.google.api.services.gmail.model.*
import faisalHelper.shared.*
import jakarta.mail.*
import jakarta.mail.internet.*
import zio.*
import zio.stream.*

import java.io.*
import java.util.*
import java.net.URL
import jakarta.activation.DataHandler
import jakarta.activation.URLDataSource
import zio.http.FormField
import jakarta.mail.util.ByteArrayDataSource

object GmailEmailSender {

  final val APPLICATION_NAME = "Faisal Helper API"

  final val JSON_FACTORY: GsonFactory = GsonFactory.getDefaultInstance()

  final val HTTP_TRANSPORT: NetHttpTransport =
    GoogleNetHttpTransport.newTrustedTransport()

  private def getCredentials(accessToken: String) = {
    GoogleCredential().setAccessToken(
      accessToken
    )
  }

  def createGmailService(accessToken: String) = {
    new Gmail.Builder(
      HTTP_TRANSPORT,
      JSON_FACTORY,
      getCredentials(accessToken)
    )
      .setApplicationName(APPLICATION_NAME)
      .build()
  }

  def sendEmail(file: Option[FormField.Binary])(
      service: Gmail,
      email: Email
  ) = {
    ZIO.attemptBlocking {
      val attachmentPart = file.map { file =>
        val part = new MimeBodyPart()
        val uds =
          new ByteArrayDataSource(file.data.toArray, file.contentType.fullType)
        part.setDataHandler(new DataHandler(uds))
        part.setDisposition(Part.ATTACHMENT)
        file.filename.foreach(part.setFileName)
        part
      }

      val messagePart = new MimeBodyPart()
      messagePart.setText(email.body)

      val multipart = new MimeMultipart()

      multipart.addBodyPart(messagePart)
      attachmentPart.foreach(multipart.addBodyPart)

      val session = Session.getDefaultInstance(new Properties(), null)
      val mimeMessage = new MimeMessage(session)
      mimeMessage.addRecipient(
        jakarta.mail.Message.RecipientType.TO,
        new InternetAddress(email.to)
      )
      mimeMessage.setSubject(email.subject)
      mimeMessage.setContent(multipart)

      val buffer = new ByteArrayOutputStream();
      mimeMessage.writeTo(buffer);
      val rawMessageBytes = buffer.toByteArray();
      val encodedEmail = Base64.encodeBase64URLSafeString(rawMessageBytes);
      val message = new com.google.api.services.gmail.model.Message();
      message.setRaw(encodedEmail);

      val user = "me"
      service.users().messages().send(user, message).execute()
    }.logError *> ZIO.logInfo(s"Successfully sent email to ${email.to}")
  }

  private def processEmails(
      input: SendEmailDto,
      file: Option[FormField.Binary] = None
  ): ZIO[Any, Throwable, Unit] = {
    for {
      _ <- ZIO.unit
      service = createGmailService(input.auth.accessToken)
      emailSender = sendEmail(file)
      emails = input.recipients
        .map(EmailGenerator.generate(input.templateInput, input.attachmentUrl))
      _ <- ZIO.foreach(emails)(x => emailSender(service, x).ignore)
    } yield ()
  }

  def emailProcessor: ZIO[zio.Queue[
    (SendEmailDto, Option[FormField.Binary])
  ], Throwable, Unit] = for {
    queue <- ZIO.service[zio.Queue[(SendEmailDto, Option[FormField.Binary])]]
    // TODO: add retry support
    _ <- ZStream
      .fromQueue(queue)
      .foreach { case (emails, attachment) =>
        processEmails(emails, attachment)
      }
  } yield ()

  def queueEmail(
      emails: SendEmailDto,
      attachment: Option[FormField.Binary] = None
  ): ZIO[zio.Queue[
    (SendEmailDto, Option[FormField.Binary])
  ], Throwable, Unit] = {
    for {
      queue <- ZIO.service[zio.Queue[(SendEmailDto, Option[FormField.Binary])]]
      _ <- queue.offer((emails, attachment))
    } yield ()
  }

  def queueLayer: ZLayer[Any, Nothing, zio.Queue[
    (SendEmailDto, Option[FormField.Binary])
  ]] =
    ZLayer.fromZIO(
      zio.Queue.bounded[(SendEmailDto, Option[FormField.Binary])](10)
    )
}
