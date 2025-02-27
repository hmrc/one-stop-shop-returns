package models.core

import base.SpecBase
import org.scalatest.EitherValues
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.{JsSuccess, Json}

class CoreSupplySpec extends SpecBase
  with ScalaCheckPropertyChecks
  with EitherValues{

  "CoreSupply" - {
    "must serialise and deserialise correctly" in {

      val supplyType = "supplyType"
      val vatRate = BigDecimal(10)
      val vatRateType = "vatRateType"
      val taxableAmountGBP = BigDecimal(11)
      val vatAmountGBP= BigDecimal(12)


      val json = Json.obj(
        "supplyType" -> "supplyType",
        "vatRate" -> 10,
        "vatRateType" -> "vatRateType",
        "taxableAmountGBP" -> 11,
        "vatAmountGBP" -> 12,
      )

      val expectedResult = CoreSupply(supplyType, vatRate, vatRateType, taxableAmountGBP, vatAmountGBP)

      Json.toJson(expectedResult) mustBe json
      json.validate[CoreSupply] mustBe JsSuccess(expectedResult)
    }
  }
}
