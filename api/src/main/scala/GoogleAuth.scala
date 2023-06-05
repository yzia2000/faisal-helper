package faisalHelper.api

import zio.*
import zio.http.*
import zio.json.*
import faisalHelper.shared.UserInfo

object GoogleAuth {
  def getVerificationUrl(accessToken: String) =
    s"https://oauth2.googleapis.com/tokeninfo?access_token=$accessToken"

  def transformUserInfo(m: Map[String, String]): Option[UserInfo] = {
    List[Option[String | Int]](
      m.get("email"),
      m.get("expires_in").flatMap(_.toIntOption)
    ).flatten match {
      case List(email: String, expires: Int) =>
        Some(UserInfo(email, expires))
      case _ => None
    }
  }

  def verify(
      accessToken: String
  ): ZIO[Client, Throwable | String, UserInfo] =
    for {
      res <- Client.request(getVerificationUrl(accessToken))
      raw <- res.body.asString
      json <- ZIO.fromEither(raw.fromJson[Map[String, String]])
      userInfo <- ZIO
        .fromOption(transformUserInfo(json))
        .mapError(_ => "Invalid token")
    } yield userInfo

}
