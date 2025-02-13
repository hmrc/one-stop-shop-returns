package models.corrections

import base.SpecBase
import crypto.EncryptedValue
import models.Quarter.Q1
import models.{EncryptedCountry, Period, StandardPeriod}
import org.scalatest.EitherValues
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.{JsSuccess, JsValue, Json}
import uk.gov.hmrc.domain.Vrn

import java.time.Instant

class EncryptedCorrectionPayloadSpec extends SpecBase
  with ScalaCheckPropertyChecks
  with EitherValues{

  "EncryptedCorrectionPayload" - {
    "must serialise and deserialise correctly" in {


      val correctionReturnPeriod: Period = StandardPeriod(2024, Q1)

      val code: EncryptedValue = EncryptedValue("value1", "nonce1")
      val name: EncryptedValue = EncryptedValue("value2", "nonce2")
      val countryVatCorrection: EncryptedValue = EncryptedValue("value2", "nonce2")

      val correctionCountry: EncryptedCountry = EncryptedCountry(code, name)
      val correctionsToCountry: List[EncryptedCorrectionToCountry] = List(EncryptedCorrectionToCountry(correctionCountry, countryVatCorrection))

      val vrn: Vrn = Vrn("Vrn")
      val period: Period = StandardPeriod(2024, Q1)
      val corrections = List(EncryptedPeriodWithCorrections(correctionReturnPeriod, correctionsToCountry))
      val submissionReceived = Instant.parse("2025-02-12T00:00:00Z")
      val lastUpdated = Instant.parse("2025-03-12T00:00:00Z")

      val json = Json.obj(
        "lastUpdated" -> "2025-03-12T00:00:00Z",
        "corrections" -> Json.arr(
          Json.obj(
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
        ),
        "vrn" -> "Vrn",
        "submissionReceived" -> "2025-02-12T00:00:00Z",
        "period" -> Json.obj(
          "year" -> 2024,
          "quarter" -> "Q1"
        )
      )

      val expectedResult = EncryptedCorrectionPayload(vrn, period, corrections, submissionReceived, lastUpdated)

      Json.toJson(expectedResult) mustBe json
      json.validate[EncryptedCorrectionPayload] mustBe JsSuccess(expectedResult)
    }
  }
}
