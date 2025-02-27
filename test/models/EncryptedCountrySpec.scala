package models

import base.SpecBase
import crypto.EncryptedValue
import org.scalatest.EitherValues
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.{JsSuccess, Json}

class EncryptedCountrySpec extends SpecBase
  with ScalaCheckPropertyChecks
  with EitherValues {

  "EncryptedCountry" - {
    "must serialise and deserialise correctly" in {
      val code: EncryptedValue = EncryptedValue("value1", "nonce1")
      val name: EncryptedValue = EncryptedValue("value2", "nonce2")

      val json = Json.obj(
        "code" -> Json.obj(
          "value" -> "value1",
          "nonce" -> "nonce1"
        ),
        "name" -> Json.obj(
          "value" -> "value2",
          "nonce" -> "nonce2"
        )
      )

      val expectedResult = EncryptedCountry(code, name)

      Json.toJson(expectedResult) mustBe json
      json.validate[EncryptedCountry] mustBe JsSuccess(expectedResult)
    }
  }
}
