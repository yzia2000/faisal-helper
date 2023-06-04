package faisalHelper.api

import faisalHelper.shared.*

object EmailGenerator {
  def fillPlaceHolders(input: GeneratorInput, template: String) =
    // TODO: add feature to allow users to dynamically set tokens
    template
      .replace("${name}", input.name)
      .replace("${company}", input.company)

  def generate(templateInput: TemplateInput, attachmentUrl: Option[String])(
      input: GeneratorInput
  ): Email = {
    val body = fillPlaceHolders(input, templateInput.bodyTemplate)
    val subject = fillPlaceHolders(input, templateInput.subjectTemplate)

    Email(input.email, subject, body, attachmentUrl)
  }
}
