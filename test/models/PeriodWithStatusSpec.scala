package models

import base.SpecBase
import models.Quarter.Q1
import models.SubmissionStatus.Due
import org.scalatest.EitherValues
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.{JsSuccess, Json}

class PeriodWithStatusSpec extends SpecBase
  with ScalaCheckPropertyChecks
  with EitherValues{

  "PeriodWithStatus" - {
    "must serialise and deserialise correctly" in {

      val period: Period = StandardPeriod(2024, Q1)
      val status: SubmissionStatus = Due

      val json = Json.obj( "period" -> Json.obj(
        "year" -> 2024,
        "quarter" -> "Q1"
        ),
        "status" -> "DUE"
      )

      val expectedResult = PeriodWithStatus(period,status)

      Json.toJson(expectedResult) mustBe json
      json.validate[PeriodWithStatus] mustBe JsSuccess(expectedResult)
    }
  }


}
