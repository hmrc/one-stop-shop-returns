package models.core

import base.SpecBase
import org.scalatest.EitherValues
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.{JsSuccess, Json}

class CorePeriodSpec extends SpecBase
  with ScalaCheckPropertyChecks
  with EitherValues{

  "CorePeriod" - {
    "must serialise and deserialise correctly" in {

      val year: Int = 2021
      val quarter: Int = 4


      val json = Json.obj(
        "year" -> 2021,
        "quarter" -> 4
      )

      val expectedResult = CorePeriod(year, quarter)

      Json.toJson(expectedResult) mustBe json
      json.validate[CorePeriod] mustBe JsSuccess(expectedResult)
    }
  }



}
