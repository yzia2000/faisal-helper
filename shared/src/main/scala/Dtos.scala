package faisalHelper.shared

import zio._
import zio.json._

case class Auth(accessToken: String)

case class UserInfo(email: String, expires: Int)

object UserInfo {
  given JsonDecoder[UserInfo] = DeriveJsonDecoder.gen[UserInfo]
  given JsonEncoder[UserInfo] = DeriveJsonEncoder.gen[UserInfo]
}

object Auth {
  given JsonDecoder[Auth] = DeriveJsonDecoder.gen[Auth]
  given JsonEncoder[Auth] = DeriveJsonEncoder.gen[Auth]
}

case class SendEmailDto(
    recipients: List[GeneratorInput],
    attachmentUrl: Option[String],
    templateInput: TemplateInput,
    auth: Auth
)

object SendEmailDto {
  given JsonDecoder[SendEmailDto] = DeriveJsonDecoder.gen[SendEmailDto]
  given JsonEncoder[SendEmailDto] = DeriveJsonEncoder.gen[SendEmailDto]
}

case class GeneratorInput(
    email: String,
    placeholders: Map[String, String]
)

object GeneratorInput {
  given JsonDecoder[GeneratorInput] = DeriveJsonDecoder.gen[GeneratorInput]
  given JsonEncoder[GeneratorInput] = DeriveJsonEncoder.gen[GeneratorInput]
}

case class TemplateInput(
    bodyTemplate: String,
    subjectTemplate: String
)

object TemplateInput {
  given JsonDecoder[TemplateInput] = DeriveJsonDecoder.gen[TemplateInput]
  given JsonEncoder[TemplateInput] = DeriveJsonEncoder.gen[TemplateInput]
}

object CsvInputReader {
  def parseInput(data: String): List[GeneratorInput] = {
    data.split("\n").toList match {
      case headerRow :: dataRows =>
        val headers = headerRow.split(",")
        dataRows.flatMap { row =>
          val placeholders = headers.zip(row.split(",")).toMap
          placeholders.get("email") match {
            case Some(email) => Some(GeneratorInput(email, placeholders))
            case _           => None
          }
        }
      case _ => List()
    }

  }
}

case class Email(
    to: String,
    subject: String,
    body: String,
    attachmentUrl: Option[String]
)
