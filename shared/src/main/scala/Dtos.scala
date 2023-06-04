package faisalHelper.shared

import zio._
import zio.json._

case class Auth(accessToken: String)

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
    name: String,
    company: String,
    email: String
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
    val lines = data.split("\n")

    lines
      .flatMap(line =>
        line.split(",") match {
          case Array(name, company, email) =>
            Some(GeneratorInput(name, company, email))
          case _ => None
        }
      )
      .toList
  }
}

case class Email(
    to: String,
    subject: String,
    body: String,
    attachmentUrl: Option[String]
)
