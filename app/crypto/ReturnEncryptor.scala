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
import services.crypto.EncryptionService
import uk.gov.hmrc.domain.Vrn

import javax.inject.Inject

class ReturnEncryptor @Inject()(
                                 appConfig: AppConfig,
                                 countryEncryptor: CountryEncryptor,
                                 crypto: AesGCMCrypto,
                                 encryptionService: EncryptionService
                               ) {

  protected val encryptionKey: String = appConfig.encryptionKey
  import countryEncryptor._

  def encryptVatOnSales(vatOnSales: VatOnSales): EncryptedVatOnSales = {
    def e(field: String): String = encryptionService.encryptField(field)

    EncryptedVatOnSales(e(vatOnSales.choice.toString), e(vatOnSales.amount.toString))
  }

  def decryptVatOnSales(vatOnSales: EncryptedVatOnSales): VatOnSales = {
    def d(field: String): String = encryptionService.decryptField(field)

    val choice = d(vatOnSales.choice) match {
      case VatOnSalesChoice.Standard.toString    => VatOnSalesChoice.Standard
      case VatOnSalesChoice.NonStandard.toString => VatOnSalesChoice.NonStandard
      case _                                     => throw new RuntimeException("Unable to decrypt value as a VatOnSalesChoice")
    }
    val amount = BigDecimal(d(vatOnSales.amount))

    VatOnSales(choice, amount)
  }

  def encryptSalesDetails(salesDetails: SalesDetails): EncryptedSalesDetails = {
    def e(field: String): String = encryptionService.encryptField(field)
    import salesDetails._

    EncryptedSalesDetails(
      encryptVatRate(vatRate),
      e(netValueOfSales.toString()),
      encryptVatOnSales(vatOnSales)
    )
  }

  def decryptSalesDetails(country: EncryptedSalesDetails): SalesDetails = {
    def d(field: String): String = encryptionService.decryptField(field)
    import country._

    SalesDetails(
      decryptVatRate(vatRate),
      BigDecimal(d(netValueOfSales)),
      decryptVatOnSales(vatOnSales)
    )
  }

  def encryptVatRate(vatRate: VatRate): EncryptedVatRate = {
    def e(field: String): String = encryptionService.encryptField(field)
    import vatRate._

    EncryptedVatRate(e(rate.toString()), e(rateType.toString))
  }

  def decryptVatRate(encryptedVatRate: EncryptedVatRate): VatRate = {
    def d(field: String): String = encryptionService.decryptField(field)
    import encryptedVatRate._

    val decryptedVatRateType =
      if (d(rateType).equals(VatRateType.Standard.toString)) {
        VatRateType.Standard
      } else {
        VatRateType.Reduced
      }

    VatRate(BigDecimal(d(rate)), decryptedVatRateType)
  }

  def encryptSalesToCountry(salesToCountry: SalesToCountry): EncryptedSalesToCountry = {
    import salesToCountry._

    EncryptedSalesToCountry(
      countryOfConsumption = encryptCountry(countryOfConsumption),
      amounts              = amounts.map(amount => encryptSalesDetails(amount))
    )
  }

  def decryptSalesToCountry(encryptedSalesToCountry: EncryptedSalesToCountry): SalesToCountry = {
    import encryptedSalesToCountry._

    SalesToCountry(
      decryptCountry(countryOfConsumption),
      amounts.map(amount => decryptSalesDetails(amount)))
  }

  def encryptEuTaxIdentifier(identifier: EuTaxIdentifier): EncryptedEuTaxIdentifier = {
    def e(field: String): String = encryptionService.encryptField(field)
    import identifier._

    EncryptedEuTaxIdentifier(e(identifierType.toString), e(value))
  }

  def decryptEuTaxIdentifier(encryptedEuTaxIdentifier: EncryptedEuTaxIdentifier): EuTaxIdentifier = {
    def d(field: String): String = encryptionService.decryptField(field)
    import encryptedEuTaxIdentifier._

    val decryptedIdentifierType =
      if (d(identifierType).equals(EuTaxIdentifierType.Vat.toString)) {
        EuTaxIdentifierType.Vat
      } else {
        EuTaxIdentifierType.Other
      }

    EuTaxIdentifier(decryptedIdentifierType, d(value))
  }

    def encryptSalesFromEuCountry(salesFromEuCountry: SalesFromEuCountry): EncryptedSalesFromEuCountry = {
    import salesFromEuCountry._

    EncryptedSalesFromEuCountry(
      countryOfSale = encryptCountry(countryOfSale),
      taxIdentifier = taxIdentifier.map(identifier => encryptEuTaxIdentifier(identifier)),
      sales = sales.map(sale => encryptSalesToCountry(sale))
    )
  }

  def decryptSalesFromEuCountry(encryptedSalesFromEuCountry: EncryptedSalesFromEuCountry): SalesFromEuCountry = {
    import encryptedSalesFromEuCountry._

    SalesFromEuCountry(
      countryOfSale = decryptCountry(countryOfSale),
      taxIdentifier = taxIdentifier.map(identifier => decryptEuTaxIdentifier(identifier)),
      sales = sales.map(sale => decryptSalesToCountry(sale))
    )
  }

  def encryptReturn(vatReturn: VatReturn, vrn: Vrn): NewEncryptedVatReturn = {
    NewEncryptedVatReturn(
      vrn = vrn,
      period = vatReturn.period,
      reference = vatReturn.reference,
      paymentReference = vatReturn.paymentReference,
      startDate = vatReturn.startDate,
      endDate = vatReturn.endDate,
      salesFromNi = vatReturn.salesFromNi.map(encryptSalesToCountry),
      salesFromEu = vatReturn.salesFromEu.map(encryptSalesFromEuCountry),
      submissionReceived = vatReturn.submissionReceived,
      lastUpdated = vatReturn.lastUpdated
    )
  }

  def decryptReturn(encryptedVatReturn: NewEncryptedVatReturn, vrn: Vrn): VatReturn = {
    VatReturn(
      vrn = vrn,
      period = encryptedVatReturn.period,
      reference = encryptedVatReturn.reference,
      paymentReference = encryptedVatReturn.paymentReference,
      startDate = encryptedVatReturn.startDate,
      endDate = encryptedVatReturn.endDate,
      salesFromNi = encryptedVatReturn.salesFromNi.map(decryptSalesToCountry),
      salesFromEu = encryptedVatReturn.salesFromEu.map(decryptSalesFromEuCountry),
      submissionReceived = encryptedVatReturn.submissionReceived,
      lastUpdated = encryptedVatReturn.lastUpdated
    )
  }

  def decryptLegacyReturn(encryptedVatReturn: LegacyEncryptedVatReturn, vrn: Vrn): VatReturn = {
    VatReturn(
      vrn = vrn,
      period = encryptedVatReturn.period,
      reference = encryptedVatReturn.reference,
      paymentReference = encryptedVatReturn.paymentReference,
      startDate = encryptedVatReturn.startDate,
      endDate = encryptedVatReturn.endDate,
      salesFromNi = encryptedVatReturn.salesFromNi.map(decryptSalesToCountry),
      salesFromEu = encryptedVatReturn.salesFromEu.map(decryptSalesFromEuCountry),
      submissionReceived = encryptedVatReturn.submissionReceived,
      lastUpdated = encryptedVatReturn.lastUpdated
    )
  }
}
