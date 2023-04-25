package faisalHelper.web

import org.scalajs.dom
import com.raquo.laminar.api.L.{*, given}
import faisalHelper.shared.GeneratorInput
import faisalHelper.shared.CsvInputReader
import io.laminext.fetch.Fetch
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalajs.dom.Response
import faisalHelper.shared.EmailDto
import zio.json._
import faisalHelper.shared.TemplateInput
import faisalHelper.shared.Auth

val subjectTemplate = Var(initial = "")
val bodyTemplate = Var(initial = "")
val recipients = Var[List[GeneratorInput]](initial = List())
val email = Var(initial = "")
val password = Var(initial = "")

def getEmailDto = EmailDto(
  recipients.now(),
  TemplateInput(
    subjectTemplate = subjectTemplate.now(),
    bodyTemplate = bodyTemplate.now()
  ),
  Auth(email = email.now(), password = password.now())
)

def fetchObserver = Observer[String](result => dom.window.alert(result))

def makeRequest(data: EmailDto) = {
  AjaxStream
    .post("http://localhost:8080/email", data = data.toJson)
    .map(_.response.toString)
}

val rootElement = div(
  textArea(
    onMountFocus,
    placeholder := "Enter list of recipients in csv format of name,company,email",
    onInput.mapToValue.map(CsvInputReader.parseInput) --> recipients
  ),
  br(),
  textArea(
    onMountFocus,
    placeholder := "Enter template of subject eg Hi ${name}",
    onInput.mapToValue --> subjectTemplate
  ),
  br(),
  textArea(
    onMountFocus,
    placeholder := "Enter template of body eg Hi ${name} from ${company}. Would like to connect with you",
    onInput.mapToValue --> bodyTemplate
  ),
  br(),
  span(
    "Number of recipients",
    child.text <-- recipients.signal.map(_.size)
  ),
  br(),
  input(
    placeholder := "Email",
    `type` := "email",
    onInput.mapToValue --> email
  ),
  input(
    placeholder := "App password",
    `type` := "password",
    onInput.mapToValue --> password
  ),
  button(
    "Submit",
    onClick.flatMap(_ => makeRequest(getEmailDto)) --> fetchObserver
  )
)

@main def main = { // In most other examples, containerNode will be set to this behind the scenes
  val containerNode = dom.document.querySelector("#app")
  render(containerNode, rootElement)
}
