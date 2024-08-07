/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package models.exclusions

import base.SpecBase
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.{JsError, JsString, JsSuccess, Json}

class ExcludedTraderSpec extends SpecBase with ScalaCheckPropertyChecks {

  private val excludedTrader: ExcludedTrader = arbitraryExcludedTrader.arbitrary.sample.value

  "ExcludedTrader" - {

    "must serialise/deserialise to and from ExcludedTrader" in {

      val json = Json.obj(
        "vrn" -> excludedTrader.vrn,
        "exclusionReason" -> excludedTrader.exclusionReason,
        "effectiveDate" -> excludedTrader.effectiveDate
      )

      val expectedResult = ExcludedTrader(
        vrn = excludedTrader.vrn,
        exclusionReason = excludedTrader.exclusionReason,
        effectiveDate = excludedTrader.effectiveDate
      )

      Json.toJson(expectedResult) mustBe json
      json.validate[ExcludedTrader] mustBe JsSuccess(expectedResult)
    }
  }

  "ExclusionReason" - {

    "must deserialise valid values" in {

      val gen = Gen.oneOf(ExclusionReason.values)

      forAll(gen) { exclusionReason =>

        JsString(exclusionReason.toString)
          .validate[ExclusionReason].asOpt.value mustBe exclusionReason
      }
    }

    "must fail to deserialise invalid values" in {

      val gen = arbitrary[String].suchThat(!ExclusionReason.values.map(_.toString).contains(_))

      forAll(gen) { invalidValue =>

        JsString(invalidValue).validate[ExclusionReason] mustBe JsError("error.invalid")
      }
    }

    "must serialise" in {

      val gen = Gen.oneOf(ExclusionReason.values)

      forAll(gen) { exclusionReason =>

        Json.toJson(exclusionReason) mustBe JsString(exclusionReason.toString)
      }
    }
  }
}

