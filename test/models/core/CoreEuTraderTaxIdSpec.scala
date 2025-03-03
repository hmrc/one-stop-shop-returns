package models.core

import base.SpecBase
import org.scalatest.EitherValues
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.{JsSuccess, Json}

class CoreEuTraderTaxIdSpec extends SpecBase
  with ScalaCheckPropertyChecks
  with EitherValues {

  "CoreEuTraderTaxId" - {
    "must serialise and deserialise correctly" in {


      val taxRefNumber: String = "taxRefNumber"
      val issuedBy: String = "issuedBy"

      val json = Json.obj(
        "taxRefNumber" -> "taxRefNumber",
        "issuedBy" -> "issuedBy"
      )

      val expectedResult = CoreEuTraderTaxId(taxRefNumber, issuedBy)

      Json.toJson(expectedResult) mustBe json
      json.validate[CoreEuTraderTaxId] mustBe JsSuccess(expectedResult)
    }
  }
}
