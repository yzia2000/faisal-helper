package faisalHelper.web

import com.raquo.laminar.api.L.{_, given}
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.raquo.waypoint._
import faisalHelper.shared.*
import faisalHelper.shared.Endpoints.*
import org.scalajs.dom
import org.scalajs.dom.*
import urldsl.vocabulary.UrlMatching
import zio.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js

lazy val subjectTemplate = Var(initial = "")
lazy val bodyTemplate = Var(initial = "")
lazy val recipients = Var[List[GeneratorInput]](initial = List())
lazy val email = Var(initial = "")
lazy val password = Var(initial = "")
lazy val attachment = Var[Option[String]](initial = None)

val tokenLabel = "access_token"

lazy val oauthClientCred =
  Var(initial = Option(dom.window.localStorage.getItem(tokenLabel)))

def parseAndStoreOauth2Token = {
  Option(
    URLSearchParams(dom.window.location.hash).get(tokenLabel)
  ) foreach {
    dom.window.localStorage.setItem(tokenLabel, _)
  }
  router.replaceState(EmailPage)
}

def logout = {
  dom.window.localStorage.removeItem(tokenLabel)
  oauthClientCred.set(None)
  router.replaceState(LoginPage)
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
  attachment.now(),
  TemplateInput(
    subjectTemplate = subjectTemplate.now(),
    bodyTemplate = bodyTemplate.now()
  ),
  Auth(accessToken = oauthClientCred.now().get)
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
    "redirect_uri" -> f"${dom.window.location.origin}/google/auth/callback",
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
def renderLoginPage =
  a(
    "Login",
    className := "btn",
    href := getLoginOauth2Link
  )

def renderEmailPage: ReactiveHtmlElement[HTMLDivElement] = {
  if (oauthClientCred.now().isEmpty) {
    router.replaceState(LoginPage)
  }

  div(
    className := "container mx-auto",
    div(
      className := "flex flex-col",
      div(
        className := "form-control"
      ),
      div(
        className := "form-control",
        button(
          "Logout",
          onClick --> (_ => logout),
          className := "btn"
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
          span(className := "label-text", "Attachment URL"),
          input(
            className := "input input-bordered input-md w-3/4",
            placeholder := "Provide url to attachment that should be sent with email",
            onInput.mapToValue.map(_ match {
              case ""            => None
              case attachmentUrl => Some(attachmentUrl)
            }) --> attachment
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
            placeholder := "Enter template of subject eg:\nHi {name}",
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
            placeholder := "Enter template of body eg:\nHi {name} from {company}. Would like to connect with you",
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

sealed trait Page {
  def title: String
}

case object EmailPage extends Page {
  def title = "Email"
}

case object LoginPage extends Page {
  def title = "Login"
}

case object LoginCallbackPage extends Page {
  def title = "Redirecting"
}

object Page {
  given JsonDecoder[Page] = DeriveJsonDecoder.gen[Page]
  given JsonEncoder[Page] = DeriveJsonEncoder.gen[Page]
}

val loginRoute = Route.static(LoginPage, root / "login" / endOfSegments)

val loginCallbackPage = Route.static(
  LoginCallbackPage,
  root / "google" / "auth" / "callback" / endOfSegments
)

val emailRoute = Route.static(
  EmailPage,
  pattern = root / "email" / endOfSegments
)

val router = new Router[Page](
  routes = List(emailRoute, loginRoute, loginCallbackPage),
  getPageTitle = "Faisal Helper | " + _.title,
  serializePage = page => page.toJson,
  routeFallback = _ =>
    if (oauthClientCred.now().isEmpty)
      LoginPage
    else EmailPage,
  deserializePage = pageString => pageString.fromJson[Page].getOrElse(LoginPage)
)(
  popStateEvents = windowEvents(
    _.onPopState
  ),
  owner = unsafeWindowOwner
)

def renderPages(page: Page) = page match {
  case EmailPage => renderEmailPage
  case LoginPage => renderLoginPage
  case LoginCallbackPage =>
    parseAndStoreOauth2Token
    div()
}

val app: Div = div(
  child <-- router.currentPageSignal.map(renderPages)
)

@main def main = {
  val containerNode = dom.document.querySelector("#app")
  renderOnDomContentLoaded(containerNode, app)
}
