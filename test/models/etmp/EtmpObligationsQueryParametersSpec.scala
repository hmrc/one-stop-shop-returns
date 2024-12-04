package models.etmp

import base.SpecBase
import play.api.libs.json.{JsSuccess, Json}

class EtmpObligationsQueryParametersSpec extends SpecBase {

  "EtmpObligationsQueryParameters" - {

    "must serialize and deserialize correctly" in {
      val queryParameters = EtmpObligationsQueryParameters(
        fromDate = "2023-01-01",
        toDate = "2023-12-31",
        status = Some("O")
      )

      val json = Json.obj(
        "fromDate" -> "2023-01-01",
        "toDate" -> "2023-12-31",
        "status" -> "O"
      )

      Json.toJson(queryParameters) mustBe json
      json.validate[EtmpObligationsQueryParameters] mustBe JsSuccess(queryParameters)
    }

    "must handle serialization and deserialization when status is None" in {
      val queryParameters = EtmpObligationsQueryParameters(
        fromDate = "2023-01-01",
        toDate = "2023-12-31",
        status = None
      )

      val json = Json.obj(
        "fromDate" -> "2023-01-01",
        "toDate" -> "2023-12-31"
      )

      Json.toJson(queryParameters) mustBe json
      json.validate[EtmpObligationsQueryParameters] mustBe JsSuccess(queryParameters)
    }

    "toSeqQueryParams must generate correct query parameters when status is present" in {
      val queryParameters = EtmpObligationsQueryParameters(
        fromDate = "2023-01-01",
        toDate = "2023-12-31",
        status = Some("O")
      )

      val expectedParams = Seq(
        "from" -> "2023-01-01",
        "to" -> "2023-12-31",
        "status" -> "O"
      )

      queryParameters.toSeqQueryParams mustBe expectedParams
    }

    "toSeqQueryParams must generate correct query parameters when status is None" in {
      val queryParameters = EtmpObligationsQueryParameters(
        fromDate = "2023-01-01",
        toDate = "2023-12-31",
        status = None
      )

      val expectedParams = Seq(
        "from" -> "2023-01-01",
        "to" -> "2023-12-31"
      )

      queryParameters.toSeqQueryParams mustBe expectedParams
    }
  }
}