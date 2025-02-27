package models

import base.SpecBase
import play.api.libs.json.{JsError, JsSuccess, Json}

class CountrySpec extends SpecBase{

  "Country" - {

    "must serialize to JSON correctly" in {
      val country = Country("AT", "Austria")

      val expectedJson = Json.obj(
        "code" -> "AT",
        "name" -> "Austria"
      )

      Json.toJson(country) mustBe expectedJson
    }

    "must deserialize from JSON correctly" in {
      val json = Json.obj(
        "code" -> "BE",
        "name" -> "Belgium"
      )

      val country = Country("BE", "Belgium")

      json.validate[Country] mustBe JsSuccess(country)
    }

    "must handle missing fields during deserialization" in {
      val json = Json.obj()

      json.validate[Country] mustBe a[JsError]
    }

    "must handle invalid data during deserialization" in {
      val json = Json.obj(
        "code" -> 12345,
        "name" -> 891011
      )

      json.validate[Country] mustBe a[JsError]
    }
  }
}
