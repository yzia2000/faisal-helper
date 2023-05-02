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
