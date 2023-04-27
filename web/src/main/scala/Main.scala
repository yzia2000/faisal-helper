package faisalHelper.web

import com.raquo.laminar.api.L.{_, given}
import faisalHelper.shared.Auth
import faisalHelper.shared.CsvInputReader
import faisalHelper.shared.GeneratorInput
import faisalHelper.shared.SendEmailDto
import faisalHelper.shared.TemplateInput
import org.scalajs.dom
import org.scalajs.dom.File
import org.scalajs.dom.Response
import org.scalajs.dom.html
import zio.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import faisalHelper.shared.Endpoints.ApiUrlPrefix
import faisalHelper.shared.Endpoints.getUrl
import faisalHelper.shared.Endpoints

lazy val subjectTemplate = Var(initial = "")
lazy val bodyTemplate = Var(initial = "")
lazy val recipients = Var[List[GeneratorInput]](initial = List())
lazy val email = Var(initial = "")
lazy val password = Var(initial = "")

val defaultApiUrl: ApiUrlPrefix = ApiUrlPrefix("http://localhost:8080")

given ApiUrlPrefix =
  js.`import`.meta.env.VITE_API_URL
    .asInstanceOf[js.UndefOr[ApiUrlPrefix]]
    .getOrElse(defaultApiUrl)

def getEmailDto = SendEmailDto(
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
      val stream = EventStream
        .fromJsPromise(req.files.head.text())
      req.value = ""
      stream
    case _ =>
      EventStream.empty
  }
}

def logger[A]: Observer[A] = Observer[A](msg => dom.console.log(msg.toString))

def makeRequest(data: SendEmailDto) = {
  AjaxStream
    .post(
      Endpoints.Email.sendEmail.getUrl,
      data = data.toJson
    )
    .map(_.response.toString)
}

lazy val rootElement = div(
  span("Recipients CSV: "),
  input(
    `type` := "file",
    onInput.flatMapStream(
      parseFile(_).map(CsvInputReader.parseInput)
    ) --> recipients
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
