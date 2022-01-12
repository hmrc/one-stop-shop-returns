/*
 * Copyright 2022 HM Revenue & Customs
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

package crypto

import models._
import play.api.libs.json.{JsObject, JsValue, Json}
import uk.gov.hmrc.domain.Vrn

import javax.inject.Inject

class SavedUserAnswersEncryptor @Inject()(
                                 crypto: SecureGCMCipher
                               ) {

  def encryptData(data: JsValue, vrn: Vrn, key: String): EncryptedValue = {
    def e(field: String): EncryptedValue = crypto.encrypt(field, vrn.vrn, key)

    e(data.toString)
  }

  def decryptData(data: EncryptedValue, vrn: Vrn, key: String): JsValue = {
    def d(field: EncryptedValue): String = crypto.decrypt(field, vrn.vrn, key)
    Json.parse(d(data))

  }

  def encryptAnswers(answers: SavedUserAnswers, vrn: Vrn, key: String): EncryptedSavedUserAnswers = {
    EncryptedSavedUserAnswers(
      vrn = vrn,
      period = answers.period,
      data = encryptData(answers.data, vrn, key),
      lastUpdated = answers.lastUpdated
    )
  }

  def decryptAnswers(encryptedAnswers: EncryptedSavedUserAnswers, vrn: Vrn, key: String): SavedUserAnswers = {
    SavedUserAnswers(
      vrn = vrn,
      period = encryptedAnswers.period,
      data = decryptData(encryptedAnswers.data, vrn, key),
      lastUpdated = encryptedAnswers.lastUpdated
    )
  }
}
