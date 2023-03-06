/*
 * Copyright 2023 HM Revenue & Customs
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
import models.corrections.{CorrectionPayload, CorrectionToCountry, EncryptedCorrectionPayload, EncryptedCorrectionToCountry, EncryptedPeriodWithCorrections, PeriodWithCorrections}
import uk.gov.hmrc.domain.Vrn

import javax.inject.Inject

class CorrectionEncryptor @Inject()(
                                     countryEncryptor: CountryEncryptor,
                                     crypto: SecureGCMCipher
                                   ) {
  import countryEncryptor._

  def encryptCorrectionToCountry(correctionToCountry: CorrectionToCountry, vrn: Vrn, key: String): EncryptedCorrectionToCountry = {
    def e(field: String): EncryptedValue = crypto.encrypt(field, vrn.vrn, key)
    import correctionToCountry._

    EncryptedCorrectionToCountry(encryptCountry(correctionCountry, vrn, key), e(countryVatCorrection.toString()))
  }

  def decryptCorrectionToCountry(encryptedCorrectionToCountry: EncryptedCorrectionToCountry, vrn: Vrn, key: String): CorrectionToCountry = {
    def d(field: EncryptedValue): String = crypto.decrypt(field, vrn.vrn, key)
    import encryptedCorrectionToCountry._

    CorrectionToCountry(
      decryptCountry(correctionCountry, vrn, key),
      BigDecimal(d(countryVatCorrection))
    )
  }

  def encryptPeriodWithCorrections(periodWithCorrections: PeriodWithCorrections, vrn: Vrn, key: String): EncryptedPeriodWithCorrections = {
    import periodWithCorrections._

    EncryptedPeriodWithCorrections(
      correctionReturnPeriod = correctionReturnPeriod,
      correctionsToCountry = correctionsToCountry.map(encryptCorrectionToCountry(_, vrn, key))
    )
  }

  def decryptPeriodWithCorrections(encryptedPeriodWithCorrections: EncryptedPeriodWithCorrections, vrn: Vrn, key: String): PeriodWithCorrections = {
    import encryptedPeriodWithCorrections._

    PeriodWithCorrections(
      correctionReturnPeriod = correctionReturnPeriod,
      correctionsToCountry = correctionsToCountry.map(decryptCorrectionToCountry(_, vrn, key))
    )
  }

  def encryptCorrectionPayload(correctionPayload: CorrectionPayload, vrn: Vrn, key: String): EncryptedCorrectionPayload = {
    EncryptedCorrectionPayload(
      vrn = vrn,
      period = correctionPayload.period,
      corrections = correctionPayload.corrections.map(encryptPeriodWithCorrections(_, vrn, key)),
      submissionReceived = correctionPayload.submissionReceived,
      lastUpdated = correctionPayload.lastUpdated
    )
  }

  def decryptCorrectionPayload(encryptedCorrectionPayload: EncryptedCorrectionPayload, vrn: Vrn, key: String): CorrectionPayload = {
    CorrectionPayload(
      vrn = vrn,
      period = encryptedCorrectionPayload.period,
      corrections = encryptedCorrectionPayload.corrections.map(decryptPeriodWithCorrections(_, vrn, key)),
      submissionReceived = encryptedCorrectionPayload.submissionReceived,
      lastUpdated = encryptedCorrectionPayload.lastUpdated
    )
  }
}
