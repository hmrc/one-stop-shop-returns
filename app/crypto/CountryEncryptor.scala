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

import models.{Country, EncryptedCountry}
import uk.gov.hmrc.domain.Vrn

import javax.inject.Inject

class CountryEncryptor @Inject()(crypto: AesGCMCrypto) {

  def encryptCountry(country: Country, vrn: Vrn, key: String): EncryptedCountry = {
    def e(field: String): EncryptedValue = crypto.encrypt(field, vrn.vrn, key)

    EncryptedCountry(e(country.code), e(country.name))
  }

  def decryptCountry(country: EncryptedCountry, vrn: Vrn, key: String): Country = {
    def d(field: EncryptedValue): String = crypto.decrypt(field, vrn.vrn, key)
    import country._

    Country(d(code), d(name))
  }

}
