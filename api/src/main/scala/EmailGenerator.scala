package faisalHelper.api

import faisalHelper.shared.*

object EmailGenerator {
  def placeholderToken(x: String) = s"{$x}"

  def getTransformers(
      placeholderMap: Map[String, String]
  ): String => String =
    placeholderMap.foldLeft[String => String](identity) {
      case (agg, (placeholder, value)) =>
        agg andThen { (template) =>
          template.replace(placeholderToken(placeholder), value)
        }
    }

  def fillPlaceHolders(input: GeneratorInput, template: String) =
    // TODO: add feature to allow users to dynamically set tokens
    getTransformers(input.placeholders + ("email" -> input.email))(template)

  def generate(templateInput: TemplateInput, attachmentUrl: Option[String])(
      input: GeneratorInput
  ): Email = {
    val body = fillPlaceHolders(input, templateInput.bodyTemplate)
    val subject = fillPlaceHolders(input, templateInput.subjectTemplate)

    Email(input.email, subject, body, attachmentUrl)
  }
}
