package models

import base.SpecBase
import crypto.EncryptedValue
import org.scalatest.EitherValues
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.{JsSuccess, Json}

class EncryptedSalesToCountrySpec extends SpecBase
  with ScalaCheckPropertyChecks
  with EitherValues {

  "EncryptedSalesToCountryCountry" - {
    "must serialise and deserialise correctly" in {

      val code: EncryptedValue = EncryptedValue("value1", "nonce1")
      val name: EncryptedValue = EncryptedValue("value2", "nonce2")

      val countryOfConsumption: EncryptedCountry = EncryptedCountry(code, name)

      val rate: EncryptedValue = EncryptedValue("value1", "nonce1")
      val rateType: EncryptedValue = EncryptedValue("value2", "nonce2")
      val vatRate: EncryptedVatRate = EncryptedVatRate(rate, rateType)

      val netValueOfSales: EncryptedValue = EncryptedValue("value3", "nonce3")

      val choice: EncryptedValue = EncryptedValue("value4", "nonce4")
      val amount: EncryptedValue = EncryptedValue("value5", "nonce5")
      val VatOnSales: EncryptedVatOnSales = EncryptedVatOnSales(choice, amount)

      val amounts: List[EncryptedSalesDetails] = List(EncryptedSalesDetails(vatRate, netValueOfSales, VatOnSales))

      val json = Json.obj(
        "countryOfConsumption" -> Json.obj(
          "code" -> Json.obj(
            "value" -> "value1",
            "nonce" -> "nonce1"
          ),
          "name" -> Json.obj(
            "value" -> "value2",
            "nonce" -> "nonce2"
          )
        ),
        "amounts" -> Json.arr(
          Json.obj(
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
        )
      )

      val expectedResult = EncryptedSalesToCountry(countryOfConsumption, amounts)

      Json.toJson(expectedResult) mustBe json
      json.validate[EncryptedSalesToCountry] mustBe JsSuccess(expectedResult)
    }
  }

}
