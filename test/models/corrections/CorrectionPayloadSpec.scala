package models.corrections

import base.SpecBase
import models.Quarter.Q1
import models.{Country, Period, StandardPeriod}
import org.scalatest.EitherValues
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.{JsSuccess, Json}
import uk.gov.hmrc.domain.Vrn

import java.time.Instant

class CorrectionPayloadSpec extends SpecBase
with ScalaCheckPropertyChecks
with EitherValues{

  "CorrectionPayload" - {
    "must serialise and deserialise correctly" in {

      val correctionCountry: Country = Country("AT", "Austria")
      val countryVatCorrection: BigDecimal = BigDecimal(100.12)

      val correctionReturnPeriod: Period = StandardPeriod(2024, Q1)
      val correctionsToCountry: List[CorrectionToCountry] = List(CorrectionToCountry(correctionCountry, countryVatCorrection))

      val vrn: Vrn = Vrn("Vrn")
      val period: Period = StandardPeriod(2024, Q1)
      val corrections = List(PeriodWithCorrections(correctionReturnPeriod, correctionsToCountry))
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
                  "code" -> "AT",
                  "name" -> "Austria"
                ),
                "countryVatCorrection" -> 100.12
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

      val expectedResult = CorrectionPayload(vrn, period, corrections, submissionReceived, lastUpdated)

      Json.toJson(expectedResult) mustBe json
      json.validate[CorrectionPayload] mustBe JsSuccess(expectedResult)
    }
  }
}
