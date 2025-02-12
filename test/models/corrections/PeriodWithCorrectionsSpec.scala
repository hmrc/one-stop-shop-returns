package models.corrections

import base.SpecBase
import models.Quarter.Q1
import models.{Country, Period, StandardPeriod}
import org.scalatest.EitherValues
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.{JsSuccess, Json}

class PeriodWithCorrectionsSpec extends SpecBase
  with ScalaCheckPropertyChecks
  with EitherValues {

  "PeriodWithCorrections" - {
    "must serialise and deserialise correctly" in {

      val correctionCountry: Country = Country("AT", "Austria")
      val countryVatCorrection: BigDecimal = BigDecimal(100.12)

      val correctionReturnPeriod: Period = StandardPeriod(2024, Q1)
      val correctionsToCountry: List[CorrectionToCountry] = List(CorrectionToCountry(correctionCountry, countryVatCorrection))

      val json = Json.obj(
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

      val expectedResult = PeriodWithCorrections(correctionReturnPeriod, correctionsToCountry)

      Json.toJson(expectedResult) mustBe json
      json.validate[PeriodWithCorrections] mustBe JsSuccess(expectedResult)
    }
  }
}
