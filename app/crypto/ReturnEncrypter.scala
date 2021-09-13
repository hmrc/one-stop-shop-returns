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

package crypto

import models._
import uk.gov.hmrc.domain.Vrn

import javax.inject.Inject

class ReturnEncrypter @Inject()(crypto: SecureGCMCipher) {

  def encryptCountry(country: Country, vrn: Vrn, key: String): EncryptedCountry = {
    def e(field: String): EncryptedValue = crypto.encrypt(field, vrn.vrn, key)

    EncryptedCountry(e(country.code), e(country.name))
  }

  def decryptCountry(country: EncryptedCountry, vrn: Vrn, key: String): Country = {
    def d(field: EncryptedValue): String = crypto.decrypt(field, vrn.vrn, key)
    import country._

    Country(d(code), d(name))
  }

  def encryptSalesDetails(salesDetails: SalesDetails, vrn: Vrn, key: String): EncryptedSalesDetails = {
    def e(field: String): EncryptedValue = crypto.encrypt(field, vrn.vrn, key)
    import salesDetails._

    EncryptedSalesDetails(
      encryptVatRate(vatRate, vrn, key),
      e(netValueOfSales.toString()),
      e(vatOnSales.toString()))
  }

  def decryptSalesDetails(country: EncryptedSalesDetails, vrn: Vrn, key: String): SalesDetails = {
    def d(field: EncryptedValue): String = crypto.decrypt(field, vrn.vrn, key)
    import country._

    SalesDetails(
      decryptVatRate(vatRate, vrn, key),
      BigDecimal(d(netValueOfSales)),
      BigDecimal(d(vatOnSales))
    )
  }

  def encryptVatRate(vatRate: VatRate, vrn: Vrn, key: String): EncryptedVatRate = {
    def e(field: String): EncryptedValue = crypto.encrypt(field, vrn.vrn, key)
    import vatRate._

    EncryptedVatRate(e(rate.toString()), e(rateType.toString))
  }

  def decryptVatRate(encryptedVatRate: EncryptedVatRate, vrn: Vrn, key: String): VatRate = {
    def d(field: EncryptedValue): String = crypto.decrypt(field, vrn.vrn, key)
    import encryptedVatRate._

    val decryptedVatRateType =
      if (d(rateType).equals(VatRateType.Standard.toString)) {
        VatRateType.Standard
      } else {
        VatRateType.Reduced
      }

    VatRate(BigDecimal(d(rate)), decryptedVatRateType)
  }

  def encryptSalesToCountry(salesToCountry: SalesToCountry, vrn: Vrn, key: String): EncryptedSalesToCountry = {
    import salesToCountry._

    EncryptedSalesToCountry(
      countryOfConsumption = encryptCountry(countryOfConsumption, vrn, key),
      amounts              = amounts.map(amount => encryptSalesDetails(amount, vrn, key))
    )
  }

  def decryptSalesToCountry(encryptedSalesToCountry: EncryptedSalesToCountry, vrn: Vrn, key: String): SalesToCountry = {
    import encryptedSalesToCountry._

    SalesToCountry(
      decryptCountry(countryOfConsumption, vrn, key),
      amounts.map(amount => decryptSalesDetails(amount, vrn, key)))
  }

  def encryptEuTaxIdentifier(identifier: EuTaxIdentifier, vrn: Vrn, key: String): EncryptedEuTaxIdentifier = {
    def e(field: String): EncryptedValue = crypto.encrypt(field, vrn.vrn, key)
    import identifier._

    EncryptedEuTaxIdentifier(e(identifierType.toString), e(value))
  }

  def decryptEuTaxIdentifier(encryptedEuTaxIdentifier: EncryptedEuTaxIdentifier, vrn: Vrn, key: String): EuTaxIdentifier = {
    def d(field: EncryptedValue): String = crypto.decrypt(field, vrn.vrn, key)
    import encryptedEuTaxIdentifier._

    val decryptedIdentifierType =
      if (d(identifierType).equals(EuTaxIdentifierType.Vat.toString)) {
        EuTaxIdentifierType.Vat
      } else {
        EuTaxIdentifierType.Other
      }

    EuTaxIdentifier(decryptedIdentifierType, d(value))
  }

    def encryptSalesFromEuCountry(salesFromEuCountry: SalesFromEuCountry, vrn: Vrn, key: String): EncryptedSalesFromEuCountry = {
    import salesFromEuCountry._

    EncryptedSalesFromEuCountry(
      countryOfSale = encryptCountry(countryOfSale, vrn, key),
      taxIdentifier = taxIdentifier.map(identifier => encryptEuTaxIdentifier(identifier, vrn, key)),
      sales = sales.map(sale => encryptSalesToCountry(sale, vrn, key))
    )
  }

  def decryptSalesFromEuCountry(encryptedSalesFromEuCountry: EncryptedSalesFromEuCountry, vrn: Vrn, key: String): SalesFromEuCountry = {
    import encryptedSalesFromEuCountry._

    SalesFromEuCountry(
      countryOfSale = decryptCountry(countryOfSale, vrn, key),
      taxIdentifier = taxIdentifier.map(identifier => decryptEuTaxIdentifier(identifier, vrn, key)),
      sales = sales.map(sale => decryptSalesToCountry(sale, vrn, key))
    )
  }

  def encryptReturn(vatReturn: VatReturn, vrn: Vrn, key: String): EncryptedVatReturn = {
    EncryptedVatReturn(
      vrn         = vrn,
      period      = vatReturn.period,
      reference   = vatReturn.reference,
      startDate   = vatReturn.startDate,
      endDate     = vatReturn.endDate,
      salesFromNi = vatReturn.salesFromNi.map(encryptSalesToCountry(_, vrn, key)),
      salesFromEu = vatReturn.salesFromEu.map(encryptSalesFromEuCountry(_, vrn, key)),
      submissionReceived = vatReturn.submissionReceived,
      lastUpdated = vatReturn.lastUpdated
    )
  }

  def decryptReturn(encryptedVatReturn: EncryptedVatReturn, vrn: Vrn, key: String): VatReturn = {
    VatReturn(
      vrn         = vrn,
      period      = encryptedVatReturn.period,
      reference   = encryptedVatReturn.reference,
      startDate   = encryptedVatReturn.startDate,
      endDate     = encryptedVatReturn.endDate,
      salesFromNi = encryptedVatReturn.salesFromNi.map(decryptSalesToCountry(_, vrn, key)),
      salesFromEu = encryptedVatReturn.salesFromEu.map(decryptSalesFromEuCountry(_, vrn, key)),
      submissionReceived = encryptedVatReturn.submissionReceived,
      lastUpdated = encryptedVatReturn.lastUpdated
    )
  }
}
