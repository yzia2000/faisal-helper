package faisalHelper.web

import org.scalajs.dom
import scala.scalajs.js
import com.raquo.laminar.api.L.{*, given}
import faisalHelper.shared.GeneratorInput
import faisalHelper.shared.CsvInputReader
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalajs.dom.Response
import faisalHelper.shared.EmailDto
import zio.json._
import faisalHelper.shared.TemplateInput
import faisalHelper.shared.Auth
import org.scalajs.dom.File
import org.scalajs.dom.html

lazy val subjectTemplate = Var(initial = "")
lazy val bodyTemplate = Var(initial = "")
lazy val recipients = Var[List[GeneratorInput]](initial = List())
lazy val email = Var(initial = "")
lazy val password = Var(initial = "")

lazy val apiUrl: String =
  js.`import`.meta.env.VITE_API_URL
    .asInstanceOf[js.UndefOr[String]]
    .getOrElse("http://localhost:8080")

def getEmailDto = EmailDto(
  recipients.now(),
  TemplateInput(
    subjectTemplate = subjectTemplate.now(),
    bodyTemplate = bodyTemplate.now()
  ),
  Auth(email = email.now(), password = password.now())
)

def fetchObserver = Observer[String](result => dom.window.alert(result))

def parseFile(e: dom.Event): EventStream[String] = {
  e.target match {
    case req: html.Input if req.files.size > 0 =>
      EventStream
        .fromJsPromise(req.files.head.text())
    case _ =>
      EventStream.empty
  }
}

def logger[A]: Observer[A] = Observer[A](msg => dom.console.log(msg.toString))

def makeRequest(data: EmailDto) = {
  AjaxStream
    .post(
      apiUrl + "/email",
      data = data.toJson
    )
    .map(_.response.toString)
}

lazy val rootElement = div(
  span("Recipients CSV: "),
  input(
    `type` := "file",
    onChange.flatMapStream(
      parseFile(_).map(CsvInputReader.parseInput)
    ) --> logger
  ),
  br(),
  textArea(
    onMountFocus,
    placeholder := "Enter template of subject eg:\nHi ${name}",
    onInput.mapToValue --> subjectTemplate
  ),
  br(),
  textArea(
    onMountFocus,
    placeholder := "Enter template of body eg:\nHi ${name} from ${company}. Would like to connect with you",
    onInput.mapToValue --> bodyTemplate
  ),
  br(),
  span(
    "Number of recipients: ",
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
