package models

import base.SpecBase
import models.corrections.CorrectionToCountry
import org.scalatest.EitherValues
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.{JsSuccess, Json}

class CorrectionToCountrySpec extends SpecBase
  with ScalaCheckPropertyChecks
  with EitherValues{

  "CorrectionToCountry" - {
    "must serialise and deserialise correctly" in {


      val correctionCountry: Country = Country("AT", "Austria")
      val countryVatCorrection: BigDecimal = BigDecimal(100.12)

      val json = Json.obj(
        "correctionCountry" -> Json.obj(
          "code" -> "AT",
          "name" -> "Austria"
        ),
        "countryVatCorrection" -> 100.12
      )

      val expectedResult = CorrectionToCountry(correctionCountry, countryVatCorrection)

      Json.toJson(expectedResult) mustBe json
      json.validate[CorrectionToCountry] mustBe JsSuccess(expectedResult)
    }
  }
}
