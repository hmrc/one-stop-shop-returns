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

import models.corrections._
import services.crypto.EncryptionService
import uk.gov.hmrc.domain.Vrn

import javax.inject.Inject

class CorrectionEncryptor @Inject()(
                                     countryEncryptor: CountryEncryptor,
                                     encryptionService: EncryptionService
                                   ) {
  import countryEncryptor._

  def encryptCorrectionToCountry(correctionToCountry: CorrectionToCountry): EncryptedCorrectionToCountry = {
    def e(field: String): String = encryptionService.encryptField(field)
    import correctionToCountry._

    EncryptedCorrectionToCountry(encryptCountry(correctionCountry), e(countryVatCorrection.toString()))
  }

  def decryptCorrectionToCountry(encryptedCorrectionToCountry: EncryptedCorrectionToCountry): CorrectionToCountry = {
    def d(field: String): String = encryptionService.decryptField(field)
    import encryptedCorrectionToCountry._

    CorrectionToCountry(
      decryptCountry(correctionCountry),
      BigDecimal(d(countryVatCorrection))
    )
  }

  def encryptPeriodWithCorrections(periodWithCorrections: PeriodWithCorrections): EncryptedPeriodWithCorrections = {
    import periodWithCorrections._

    EncryptedPeriodWithCorrections(
      correctionReturnPeriod = correctionReturnPeriod,
      correctionsToCountry = correctionsToCountry.map(encryptCorrectionToCountry)
    )
  }

  def decryptPeriodWithCorrections(encryptedPeriodWithCorrections: EncryptedPeriodWithCorrections): PeriodWithCorrections = {
    import encryptedPeriodWithCorrections._

    PeriodWithCorrections(
      correctionReturnPeriod = correctionReturnPeriod,
      correctionsToCountry = correctionsToCountry.map(decryptCorrectionToCountry)
    )
  }

  def encryptCorrectionPayload(correctionPayload: CorrectionPayload, vrn: Vrn): EncryptedCorrectionPayload = {
    EncryptedCorrectionPayload(
      vrn = vrn,
      period = correctionPayload.period,
      corrections = correctionPayload.corrections.map(encryptPeriodWithCorrections),
      submissionReceived = correctionPayload.submissionReceived,
      lastUpdated = correctionPayload.lastUpdated
    )
  }

  def decryptCorrectionPayload(encryptedCorrectionPayload: EncryptedCorrectionPayload, vrn: Vrn): CorrectionPayload = {
    CorrectionPayload(
      vrn = vrn,
      period = encryptedCorrectionPayload.period,
      corrections = encryptedCorrectionPayload.corrections.map(decryptPeriodWithCorrections),
      submissionReceived = encryptedCorrectionPayload.submissionReceived,
      lastUpdated = encryptedCorrectionPayload.lastUpdated
    )
  }
}
