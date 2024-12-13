/*
 * Copyright 2021 HM Revenue & Customs
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

package models

import base.SpecBase
import crypto.EncryptedValue
import org.scalatest.EitherValues
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.{JsSuccess, Json}


class EncryptedVatRateSpec
  extends SpecBase
    with ScalaCheckPropertyChecks
    with EitherValues {

  "EncryptedVatRate" - {
    "must serialise and deserialise correctly" in {
      val rate: EncryptedValue =  EncryptedValue("value1", "nonce1")
      val rateType: EncryptedValue =  EncryptedValue("value2", "nonce2")

      val json = Json.obj(
        "rate" -> Json.obj(
          "value" -> "value1",
          "nonce" -> "nonce1"
        ),
        "rateType" -> Json.obj(
          "value" -> "value2",
          "nonce" -> "nonce2"
        )
      )

      val expectedResult = EncryptedVatRate(rate, rateType)

      Json.toJson(expectedResult) mustBe json
      json.validate[EncryptedVatRate] mustBe JsSuccess(expectedResult)
    }
  }
}
