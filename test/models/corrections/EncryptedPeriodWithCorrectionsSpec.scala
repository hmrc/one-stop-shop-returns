package models.corrections

import base.SpecBase
import crypto.EncryptedValue
import models.Quarter.Q1
import models.{EncryptedCountry, Period, StandardPeriod}
import org.scalatest.EitherValues
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.{JsSuccess, Json}

class EncryptedPeriodWithCorrectionsSpec extends SpecBase
  with ScalaCheckPropertyChecks
  with EitherValues {

  "EncryptedPeriodWithCorrections" - {
    "must serialise and deserialise correctly" in {

      val code: EncryptedValue = EncryptedValue("value1", "nonce1")
      val name: EncryptedValue = EncryptedValue("value2", "nonce2")
      val correctionCountry: EncryptedCountry = EncryptedCountry(code, name)
      val countryVatCorrection: EncryptedValue = EncryptedValue("value2", "nonce2")

      val correctionReturnPeriod: Period = StandardPeriod(2024, Q1)
      val correctionsToCountry: List[EncryptedCorrectionToCountry] = List(EncryptedCorrectionToCountry(correctionCountry, countryVatCorrection))

      val json = Json.obj(
        "correctionReturnPeriod" -> Json.obj(
          "year" -> 2024,
          "quarter" -> "Q1"
        ),
        "correctionsToCountry" -> Json.arr(
          Json.obj(
            "correctionCountry" -> Json.obj(
              "code" -> Json.obj(
                "value" -> "value1",
                "nonce" -> "nonce1"
              ),
              "name" -> Json.obj(
                "value" -> "value2",
                "nonce" -> "nonce2"
              )
            ),
            "countryVatCorrection" -> Json.obj(
              "value" -> "value2",
              "nonce" -> "nonce2"
            )
          )
        )
      )

      val expectedResult = EncryptedPeriodWithCorrections(correctionReturnPeriod, correctionsToCountry)

      Json.toJson(expectedResult) mustBe json
      json.validate[EncryptedPeriodWithCorrections] mustBe JsSuccess(expectedResult)
    }
  }
}
