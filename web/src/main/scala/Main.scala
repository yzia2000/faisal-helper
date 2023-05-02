package faisalHelper.web

import com.raquo.laminar.api.L.{_, given}
import faisalHelper.shared.Auth
import faisalHelper.shared.CsvInputReader
import faisalHelper.shared.Endpoints
import faisalHelper.shared.Endpoints.ApiUrlPrefix
import faisalHelper.shared.Endpoints.getUrl
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
import org.scalajs.dom.URLSearchParams

lazy val subjectTemplate = Var(initial = "")
lazy val bodyTemplate = Var(initial = "")
lazy val recipients = Var[List[GeneratorInput]](initial = List())
lazy val email = Var(initial = "")
lazy val password = Var(initial = "")

val tokenLabel = "access_token"

lazy val oauthClientCred =
  Var(initial = Option(dom.window.localStorage.getItem(tokenLabel)))

def oauth2TokenInLocalStorage = {
  Option(
    URLSearchParams(dom.window.location.hash).get(tokenLabel)
  ) foreach {
    dom.window.localStorage.setItem(tokenLabel, _)
  }
}

def logout = {
  dom.window.localStorage.removeItem(tokenLabel)
  oauthClientCred.set(None)
}

val defaultApiUrl: ApiUrlPrefix = ApiUrlPrefix("http://localhost:8080")

given ApiUrlPrefix =
  js.`import`.meta.env.VITE_API_URL
    .asInstanceOf[js.UndefOr[ApiUrlPrefix]]
    .getOrElse(defaultApiUrl)

val oauth2ClientId =
  js.`import`.meta.env.VITE_GOOGLE_OAUTH2_CLIENT_ID
    .asInstanceOf[String]

def getEmailDto = SendEmailDto(
  recipients.now(),
  TemplateInput(
    subjectTemplate = subjectTemplate.now(),
    bodyTemplate = bodyTemplate.now()
  ),
  Auth(accessToken =
    oauthClientCred.now().getOrElse("")
  ) // TODO: raise alert here
)

def fetchObserver = Observer[String](result => dom.window.alert(result))

def parseFile(e: dom.Event): EventStream[String] = {
  e.target match {
    case req: html.Input if req.files.size > 0 =>
      val stream = EventStream
        .fromJsPromise(req.files.head.text())
      stream
    case _ =>
      EventStream.empty
  }
}

def logger[A]: Observer[A] = Observer[A](msg => dom.console.log(msg.toString))

def sendEmail(data: SendEmailDto) = {
  AjaxStream
    .post(
      Endpoints.Email.sendEmail.getUrl,
      data = data.toJson
    )
    .map(_.response.toString)
}

val googleScope = "https://www.googleapis.com/auth/gmail.send"

def getLoginOauth2Link = {
  val oauth2Endpoint = "https://accounts.google.com/o/oauth2/v2/auth";
  val requestBody = js.Dictionary(
    "client_id" -> oauth2ClientId,
    "response_type" -> "token",
    "redirect_uri" -> dom.window.location.origin,
    "scope" -> googleScope,
    "include_granted_scopes" -> "true",
    "state" -> "pass-through"
  )
  val urlSearchParam = URLSearchParams(requestBody)
  val url = oauth2Endpoint + "?" + urlSearchParam.toString()
  url
}

def processAuth() = {
  if (oauthClientCred.now().isEmpty) {
    oauthClientCred.set(Option("Logged In"))
    EventStream.empty
  } else {
    oauthClientCred.set(None)
    EventStream.empty
  }
}

def rootElement = {
  oauth2TokenInLocalStorage

  div(
    className := "container mx-auto",
    div(
      className := "flex flex-col",
      div(
        className := "form-control",
        a(
          "Login",
          className <-- oauthClientCred.toObservable.map(x =>
            if x.isDefined then "btn hidden" else "btn"
          ),
          href := getLoginOauth2Link
        )
      ),
      div(
        className := "form-control",
        button(
          "Logout",
          hidden <-- oauthClientCred.toObservable.map(_.isEmpty),
          onClick --> (_ => logout),
          className <-- oauthClientCred.toObservable.map(x =>
            if x.isEmpty then "btn hidden" else "btn"
          )
        )
      ),
      div(
        className := "form-control",
        label(
          className := "label",
          span(className := "label-text", "Recipients CSV"),
          input(
            `type` := "file",
            className := "file-input file-input-md",
            onInput.flatMapStream(
              parseFile(_).map(CsvInputReader.parseInput)
            ) --> recipients
          ),
          span(
            "Number of recipients: ",
            child.text <-- recipients.signal.map(_.size)
          )
        )
      ),
      div(
        className := "form-control",
        label(
          className := "label",
          span(className := "label-text", "Subject Template"),
          textArea(
            onMountFocus,
            className := "textarea textarea-bordered textarea-md w-3/4",
            placeholder := "Enter template of subject eg:\nHi ${name}",
            onInput.mapToValue --> subjectTemplate
          )
        )
      ),
      div(
        className := "form-control",
        label(
          className := "label",
          span(className := "label-text", "Body Template"),
          textArea(
            onMountFocus,
            className := "textarea textarea-bordered textarea-md w-3/4",
            placeholder := "Enter template of body eg:\nHi ${name} from ${company}. Would like to connect with you",
            onInput.mapToValue --> bodyTemplate
          )
        )
      ),
      button(
        "Submit",
        className := "btn",
        onClick.flatMap(_ => sendEmail(getEmailDto)) --> fetchObserver
      )
    )
  )
}

@main def main = { // In most other examples, containerNode will be set to this behind the scenes
  val containerNode = dom.document.querySelector("#app")
  render(containerNode, rootElement)
}
