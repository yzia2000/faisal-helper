package faisalHelper.shared

object Endpoints {
  // Not an opaque type as otherwise we will have issues with path
  // declaration on api
  type Endpoint = String

  opaque type ApiUrlPrefix = String
  object ApiUrlPrefix {
    def apply(x: String): ApiUrlPrefix = x
  }

  extension (endpoint: Endpoint) {
    def getUrl(using apiUrlPrefix: ApiUrlPrefix) = apiUrlPrefix + "/" + endpoint
  }

  object Email {
    final val sendEmail: Endpoint = "email"
  }
}
