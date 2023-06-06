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
lazy val attachmentUrl = Var[Option[String]](initial = None)
lazy val attachmentFile = Var[Option[File]](initial = None)

val tokenLabel = "access_token"

lazy val oauthAccessToken =
  Var(initial = Option(dom.window.localStorage.getItem(tokenLabel)))

lazy val userInfo = Var[Option[UserInfo]](initial = None)

lazy val authObserver =
  Observer[Option[UserInfo]](userInfoOpt =>
    userInfoOpt match {
      case None => logout
      case value @ Some(info) =>
        userInfo.set(value)
        EventStream
          .delay((info.expires + 5) * 1000)
          .addObserver(
            Observer[Unit](_ => {
              dom.window.alert("Session timed out")
              logout
            })
          )(unsafeWindowOwner)
    }
  )

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
  oauthAccessToken.set(None)
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
  attachmentUrl.now(),
  TemplateInput(
    subjectTemplate = subjectTemplate.now(),
    bodyTemplate = bodyTemplate.now()
  ),
  Auth(accessToken = oauthAccessToken.now().get)
)

def getPreviewEmailDto = getEmailDto.copy(recipients =
  userInfo
    .now()
    .map(info =>
      recipients.now().headOption.toList.map(_.copy(email = info.email))
    )
    .getOrElse(List())
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

def getFile(e: dom.Event): EventStream[Option[dom.File]] = {
  e.target match {
    case req: html.Input =>
      EventStream.fromValue(req.files.headOption)
    case _ =>
      EventStream.empty
  }
}

def logger[A]: Observer[A] = Observer[A](msg => dom.console.log(msg.toString))

def sendEmail(data: SendEmailDto) = {
  val formData = new FormData()
  formData.append("data", data.toJson)
  attachmentFile.now().foreach(file => formData.append("attachment", file))
  AjaxStream
    .post(
      Endpoints.Email.sendEmail.getUrl,
      data = formData
    )
    .map(_.response.toString)
    .recover { case x: Throwable => Some(x.getMessage()) }
}

val googleScope =
  "https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/gmail.send"

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

def checkAuthorized = {
  oauthAccessToken.now() match {
    case None => EventStream.empty
    case Some(accessToken) =>
      AjaxStream
        .get(
          url = Endpoints.Authentication.google.getUrl,
          headers = Map("Authorization" -> s"Bearer $accessToken")
        )
        .map(_.responseText)
        // TODO: please make this less disgusting
        .recover { case _: Throwable =>
          Some("")
        }
        .map(_.fromJson[UserInfo])
        .collect {
          case Right(userInfo) =>
            Some(userInfo)
          case _ => None
        }
  }
}

def renderLoginPage =
  a(
    "Login",
    className := "btn",
    href := getLoginOauth2Link
  )

def renderEmailPage: ReactiveHtmlElement[HTMLDivElement] = {
  div(
    onMountBind(_ => (checkAuthorized --> authObserver)),
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
          span(className := "label-text", "Attachment"),
          input(
            `type` := "file",
            className := "file-input file-input-md",
            onInput.flatMapStream(
              getFile
            ) --> attachmentFile
          )
        )
      ),
      div(
        className := "form-control",
        label(
          className := "label",
          span(className := "label-text", "Subject Template"),
          textArea(
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
            className := "textarea textarea-bordered textarea-md w-3/4",
            placeholder := "Enter template of body eg:\nHi {name} from {company}. Would like to connect with you",
            onInput.mapToValue --> bodyTemplate
          )
        )
      ),
      button(
        child.text <-- userInfo.toObservable.map(x =>
          x match {
            case Some(info) => s"Send me a preview @ ${info.email}!"
            case _          => "Loading..."
          }
        ),
        className := "btn",
        onClick.flatMap(_ => sendEmail(getPreviewEmailDto)) --> fetchObserver
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
    if (oauthAccessToken.now().isEmpty)
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

val app: Div = {
  div(
    child <-- router.currentPageSignal.map(renderPages)
  )
}

@main def main = {
  val containerNode = dom.document.querySelector("#app")
  renderOnDomContentLoaded(containerNode, app)
}
