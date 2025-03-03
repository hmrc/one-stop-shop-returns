package models

import base.SpecBase
import crypto.EncryptedValue
import org.scalatest.EitherValues
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.{JsSuccess, Json}

class EncryptedSalesDetailsSpec extends SpecBase
  with ScalaCheckPropertyChecks
  with EitherValues {

  "EncryptedSalesDetails" - {
    "must serialise and deserialise correctly" in {
      
      val rate: EncryptedValue = EncryptedValue("value1", "nonce1")
      val rateType: EncryptedValue = EncryptedValue("value2", "nonce2")
      val vatRate: EncryptedVatRate = EncryptedVatRate(rate, rateType)

      val netValueOfSales: EncryptedValue = EncryptedValue("value3", "nonce3")

      val choice :EncryptedValue= EncryptedValue("value4", "nonce4")
      val amount :EncryptedValue = EncryptedValue("value5", "nonce5")
      val VatOnSales :EncryptedVatOnSales = EncryptedVatOnSales(choice, amount)

      val json = Json.obj(
        "vatRate" -> Json.obj(
          "rate" -> Json.obj(
            "value" -> "value1",
            "nonce" -> "nonce1"
          ),
          "rateType" -> Json.obj(
            "value" -> "value2",
            "nonce" -> "nonce2"
          )

        ),
        "netValueOfSales" -> Json.obj(
          "value" -> "value3",
          "nonce" -> "nonce3"
        ),
        "vatOnSales" -> Json.obj(
          "choice" -> Json.obj(
            "value" -> "value4",
            "nonce" -> "nonce4"
          ),
          "amount" -> Json.obj(
            "value" -> "value5",
            "nonce" -> "nonce5"
          )
        )
      )

      val expectedResult = EncryptedSalesDetails(vatRate, netValueOfSales, VatOnSales)

      Json.toJson(expectedResult) mustBe json
      json.validate[EncryptedSalesDetails] mustBe JsSuccess(expectedResult)
    }
  }
}
