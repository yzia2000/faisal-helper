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

  def sendEmail(
      service: Gmail,
      email: Email
  ) = {
    ZIO.attemptBlocking {
      val attachmentPart = email.attachmentUrl.map { attachmentUrl =>
        val part = new MimeBodyPart()
        val url = new URL(
          attachmentUrl
        )
        val uds = new URLDataSource(url)
        part.setDataHandler(new DataHandler(uds))
        part.setDisposition(Part.ATTACHMENT)
        part.setFileName(
          url.getFile().split("/").last
        )
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

  private def processEmails(input: SendEmailDto): ZIO[Any, Throwable, Unit] = {
    for {
      _ <- ZIO.unit
      service = createGmailService(input.auth.accessToken)
      emails = input.recipients
        .map(EmailGenerator.generate(input.templateInput, input.attachmentUrl))
      _ <- ZIO.foreach(emails)(x => sendEmail(service, x).ignore)
    } yield ()
  }

  def emailProcessor: ZIO[zio.Queue[SendEmailDto], Throwable, Unit] = for {
    queue <- ZIO.service[zio.Queue[SendEmailDto]]
    // TODO: add retry support
    _ <- ZStream
      .fromQueue(queue)
      .foreach(processEmails)
  } yield ()

  def queueEmail(
      emails: SendEmailDto
  ): ZIO[zio.Queue[SendEmailDto], Throwable, Unit] = {
    for {
      queue <- ZIO.service[zio.Queue[SendEmailDto]]
      _ <- queue.offer(emails)
    } yield ()
  }

  def queueLayer: ZLayer[Any, Nothing, zio.Queue[SendEmailDto]] =
    ZLayer.fromZIO(zio.Queue.bounded[SendEmailDto](10))
}
