package models

import base.SpecBase
import models.Quarter.Q1
import org.scalatest.EitherValues
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.{JsSuccess, JsValue, Json}
import uk.gov.hmrc.domain.Vrn

import java.time.Instant

class SavedUserAnswersSpec extends SpecBase
  with ScalaCheckPropertyChecks
  with EitherValues {

  "SavedUserAnswers" - {
    "must serialise and deserialise correctly" in {

      val vrn: Vrn = Vrn("Vrn")
      val period: Period = StandardPeriod(2024, Q1)
      val data: JsValue = Json.obj(
        "data" -> "data"
      )
      val lastUpdated: Instant = Instant.parse("2025-02-12T00:00:00Z")

      val json = Json.obj(
        "vrn" -> "Vrn",
        "period" -> Json.obj(
          "year" -> 2024,
          "quarter" -> "Q1"
        ),
        "data" -> Json.obj(
          "data" -> "data"
        ),
        "lastUpdated" -> "2025-02-12T00:00:00Z"
      )

      val expectedResult = SavedUserAnswers(vrn, period, data, lastUpdated)

      Json.toJson(expectedResult) mustBe json
      json.validate[SavedUserAnswers] mustBe JsSuccess(expectedResult)
    }
  }
}
