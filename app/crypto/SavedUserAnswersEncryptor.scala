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

package crypto

import config.AppConfig
import models._
import play.api.libs.json.Json
import services.crypto.EncryptionService
import uk.gov.hmrc.domain.Vrn

import javax.inject.Inject

class SavedUserAnswersEncryptor @Inject()(
                                           appConfig: AppConfig,
                                           crypto: AesGCMCrypto,
                                           encryptionService: EncryptionService
                                         ) {

  protected val encryptionKey: String = appConfig.encryptionKey

  def encryptAnswers(answers: SavedUserAnswers, vrn: Vrn): EncryptedSavedUserAnswers = {
    def encryptAnswerValue(answerValue: String): String = encryptionService.encryptField(answerValue)

    NewEncryptedSavedUserAnswers(
      vrn = vrn,
      period = answers.period,
      data = encryptAnswerValue(answers.data.toString),
      lastUpdated = answers.lastUpdated
    )
  }

  def decryptAnswers(encryptedAnswers: NewEncryptedSavedUserAnswers, vrn: Vrn): SavedUserAnswers = {
    def decryptAnswerValue(answerValue: String): String = encryptionService.decryptField(answerValue)

    SavedUserAnswers(
      vrn = vrn,
      period = encryptedAnswers.period,
      data = Json.parse(decryptAnswerValue(encryptedAnswers.data)),
      lastUpdated = encryptedAnswers.lastUpdated
    )
  }

  def decryptLegacyAnswers(encryptedAnswers: LegacyEncryptedSavedUserAnswers, vrn: Vrn): SavedUserAnswers = {
    def decryptAnswerValue(answerValue: EncryptedValue): String = crypto.decrypt(answerValue, vrn.vrn, encryptionKey)

    SavedUserAnswers(
      vrn = vrn,
      period = encryptedAnswers.period,
      data = Json.parse(decryptAnswerValue(encryptedAnswers.data)),
      lastUpdated = encryptedAnswers.lastUpdated
    )
  }
}
