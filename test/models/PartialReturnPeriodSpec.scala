package models

import java.time.LocalDate
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsSuccess, Json}

class PartialReturnPeriodSpec extends AnyWordSpec with Matchers {

  private val firstDay = LocalDate.of(2024, 1, 1)
  private val lastDay= LocalDate.of(2024, 3, 31)

  val partialReturnPeriod: PartialReturnPeriod = PartialReturnPeriod(firstDay, lastDay, 2024, Quarter.Q1)

  "PartialReturnPeriod" should {
    "serialise and deserialise correctly" in {
      val expectedJson = Json.obj(
        "firstDay" -> "2024-01-01",
        "lastDay" -> "2024-03-31",
        "year" -> 2024,
        "quarter" -> "Q1"
      )

      val json = Json.toJson(partialReturnPeriod)
      json mustBe expectedJson
      json.validate[PartialReturnPeriod] mustEqual JsSuccess(partialReturnPeriod)
    }
  }
}