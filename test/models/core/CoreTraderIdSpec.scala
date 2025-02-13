package models.core

import base.SpecBase
import org.scalatest.EitherValues
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.{JsSuccess, Json}

class CoreTraderIdSpec extends SpecBase
  with ScalaCheckPropertyChecks
  with EitherValues{

  "CoreTraderId" - {
    "must serialise and deserialise correctly" in {

      val vatNumber = "12345"
      val issuedBy = "issuedBy"


      val json = Json.obj(
        "vatNumber" -> "12345",
        "issuedBy" -> "issuedBy"
      )

      val expectedResult = CoreTraderId(vatNumber, issuedBy)

      Json.toJson(expectedResult) mustBe json
      json.validate[CoreTraderId] mustBe JsSuccess(expectedResult)
    }
  }

}
