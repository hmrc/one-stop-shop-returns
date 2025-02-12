package models

import base.SpecBase
import crypto.EncryptedValue
import org.scalatest.EitherValues
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.{JsSuccess, Json}

class EncryptedEuTaxIdentifierSpec extends SpecBase
with ScalaCheckPropertyChecks
with EitherValues {

  "EncryptedEuTaxIdentifier" - {
    "must serialise and deserialise correctly" in {
      
      val identifierType: EncryptedValue = EncryptedValue("value1", "nonce1")
      val value: EncryptedValue = EncryptedValue("value2", "nonce2")

      val json = Json.obj(
        "identifierType" -> Json.obj(
          "value" -> "value1",
          "nonce" -> "nonce1"
        ),
        "value" -> Json.obj(
          "value" -> "value2",
          "nonce" -> "nonce2"
        )
      )

      val expectedResult = EncryptedEuTaxIdentifier(identifierType, value)

      Json.toJson(expectedResult) mustBe json
      json.validate[EncryptedEuTaxIdentifier] mustBe JsSuccess(expectedResult)
    }
  }
}
