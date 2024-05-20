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
import play.api.libs.json.{JsSuccess, Json}

class ExcludedTraderSpec extends SpecBase {

  private val excludedTrader: ExcludedTrader = arbitraryExcludedTrader.arbitrary.sample.value

  "ExcludedTrader" - {

    "must serialise/deserialise to and from ExcludedTrader" in {

      val json = Json.obj(
        "vrn" -> excludedTrader.vrn,
        "exclusionReason" -> excludedTrader.exclusionReason,
        "effectivePeriod" -> excludedTrader.effectivePeriod
      )

      val expectedResult = ExcludedTrader(
        vrn = excludedTrader.vrn,
        exclusionReason = excludedTrader.exclusionReason,
        effectivePeriod = excludedTrader.effectivePeriod
      )

      Json.toJson(expectedResult) mustBe json
      json.validate[ExcludedTrader] mustBe JsSuccess(expectedResult)
    }
  }
}
