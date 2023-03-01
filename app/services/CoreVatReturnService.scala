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

package services

import connectors.RegistrationConnector
import logging.Logging
import models._
import models.core._
import models.corrections.{CorrectionPayload, PeriodWithCorrections}
import models.domain._
import models.domain.EuTaxIdentifierType.Vat
import uk.gov.hmrc.http.HeaderCarrier
import utils.CorrectionUtils
import utils.ObfuscationUtils.obfuscateVrn

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class CoreVatReturnService @Inject()(
                                      vatReturnSalesService: VatReturnSalesService,
                                      registrationConnector: RegistrationConnector
                                    )
                                    (implicit ec: ExecutionContext) extends Logging {


  def toCore(vatReturn: VatReturn, correctionPayload: CorrectionPayload)(implicit hc: HeaderCarrier): Future[CoreVatReturn] = {
    registrationConnector.getRegistration().flatMap {
      case Some(registration) =>
        toCore(vatReturn, correctionPayload, registration)
      case _ =>
        val errorMessage = s"Unable to get registration for ${obfuscateVrn(vatReturn.vrn)} in period ${vatReturn.period}"
        logger.error(errorMessage)
        Future.failed(new Exception(errorMessage))
    }
  }

  def toCore(vatReturn: VatReturn, correctionPayload: CorrectionPayload, registration: Registration): Future[CoreVatReturn] = {
    val totalVatDue = vatReturnSalesService.getTotalVatOnSalesAfterCorrection(vatReturn, Some(correctionPayload))
    val amountsToCountries = CorrectionUtils.groupByCountryAndSum(correctionPayload, vatReturn)

    Future.successful(CoreVatReturn(
      vatReturnReferenceNumber = vatReturn.reference.value,
      version = vatReturn.lastUpdated,
      traderId = CoreTraderId(
        vatNumber = vatReturn.vrn.vrn,
        issuedBy = "XI"
      ),
      period = CorePeriod(
        year = vatReturn.period.year,
        quarter = vatReturn.period.quarter.toString.tail.toInt
      ),
      startDate = vatReturn.startDate.getOrElse(vatReturn.period.firstDay),
      endDate = vatReturn.endDate.getOrElse(vatReturn.period.lastDay),
      submissionDateTime = vatReturn.submissionReceived,
      totalAmountVatDueGBP = totalVatDue,
      msconSupplies = toCoreMsconSupplies(vatReturn.salesFromNi, vatReturn.salesFromEu, correctionPayload.corrections, amountsToCountries, registration)
    ))
  }

  private def toCoreMsconSupplies(salesFromNi: List[SalesToCountry], salesFromEu: List[SalesFromEuCountry], corrections: List[PeriodWithCorrections], amountsToCountries: Map[Country, CountryAmounts], registration: Registration): List[CoreMsconSupply] = {
    val listOfCountriesSoldTo = (
      salesFromNi.map(_.countryOfConsumption)
        ++ salesFromEu.flatMap(_.sales.map(_.countryOfConsumption))
        ++ corrections.flatMap(_.correctionsToCountry.map(_.correctionCountry))
      ).distinct

    val listOfSalesFromNiAsCoreSupplies = toCoreSupplies(salesFromNi)
    val listOfSalesFromEuAsCoreMsestSupplies = toCoreMsestSupplies(salesFromEu, registration)
    val coreCorrections = toCoreCorrections(corrections)

    listOfCountriesSoldTo.map { countrySoldTo =>

      val amountToCountry = amountsToCountries.getOrElse(countrySoldTo, CountryAmounts(0, 0, 0))

      CoreMsconSupply(
        msconCountryCode = countrySoldTo.code,
        balanceOfVatDueGBP = amountToCountry.totalVat,
        grandTotalMsidGoodsGBP = amountToCountry.totalVatFromNi,
        grandTotalMsestGoodsGBP = amountToCountry.totalVatFromEu,
        correctionsTotalGBP = amountToCountry.totalVatFromCorrection,
        msidSupplies = listOfSalesFromNiAsCoreSupplies.getOrElse(countrySoldTo, List.empty),
        msestSupplies = listOfSalesFromEuAsCoreMsestSupplies.getOrElse(countrySoldTo, List.empty),
        corrections = coreCorrections.getOrElse(countrySoldTo, List.empty)
      )
    }
  }

  private def toCoreSupplies(salesToCountry: List[SalesToCountry]): Map[Country, List[CoreSupply]] = {

    (for {
      saleToCountry <- salesToCountry
      amount <- saleToCountry.amounts
    } yield {
      saleToCountry.countryOfConsumption ->
        List(toCoreSupply(amount))
    }).toMap
  }

  private def toCoreSupply(salesDetails: SalesDetails): CoreSupply = {
    CoreSupply(
      "GOODS",
      salesDetails.vatRate.rate,
      salesDetails.vatRate.rateType.toString,
      salesDetails.netValueOfSales,
      salesDetails.vatOnSales.amount
    )
  }

  private def toCoreMsestSupplies(salesFromEuCountry: List[SalesFromEuCountry], registration: Registration): Map[Country, List[CoreMsestSupply]] = {
    val allSoldToEuCountries = salesFromEuCountry.flatMap(_.sales.map(_.countryOfConsumption)).distinct

    allSoldToEuCountries.map { soldToEuCountry =>

      val allSalesForCountry = salesFromEuCountry.map { saleFromEu =>
        saleFromEu.countryOfSale -> (for {
          saleToCountry <- saleFromEu.sales.filter(_.countryOfConsumption == soldToEuCountry)
          amount <- saleToCountry.amounts
        } yield toCoreSupply(amount))
      }.toMap

      val onlyNonEmptySalesForCountry = allSalesForCountry.filter(_._2.nonEmpty)

      val coreMsestSuppliesForCountry = onlyNonEmptySalesForCountry.map { case (countrySoldFrom, sales) =>

        getEuTraderIdForCountry(countrySoldFrom, registration) match {
          case Some(coreEuTraderId: CoreEuTraderId) =>
            CoreMsestSupply(
              countryCode = None,
              euTraderId = Some(coreEuTraderId),
              supplies = sales
            )
          case _ =>
            CoreMsestSupply(
              countryCode = Some(countrySoldFrom.code),
              euTraderId = None,
              supplies = sales
            )
        }
      }

      soldToEuCountry -> coreMsestSuppliesForCountry.toList
    }.toMap

  }

  private def getEuTraderIdForCountry(country: Country, registration: Registration): Option[CoreEuTraderId] = {
    val matchedRegistration = registration.euRegistrations.filter {
      case euRegistration: EuVatRegistration => euRegistration.country == country
      case euRegistrationWithFE: RegistrationWithFixedEstablishment => euRegistrationWithFE.country == country
      case euRegistrationWithoutTaxId: RegistrationWithoutTaxId => euRegistrationWithoutTaxId.country == country
      case euRegistrationSendingGoods: RegistrationWithoutFixedEstablishmentWithTradeDetails => euRegistrationSendingGoods.country == country
      case euRegistrationWithoutFE: RegistrationWithoutFixedEstablishment => euRegistrationWithoutFE.country == country
    }

    matchedRegistration.headOption.flatMap {
      case euRegistrationWithFE: RegistrationWithFixedEstablishment =>
        val strippedTaxIdentifier = convertTaxIdentifierForTransfer(euRegistrationWithFE.taxIdentifier.value, euRegistrationWithFE.country.code)
        extractFormattedTaxId(strippedTaxIdentifier, euRegistrationWithFE.taxIdentifier.identifierType, euRegistrationWithFE.country.code)
      case euRegistrationWithoutFEWithTaxDetails: RegistrationWithoutFixedEstablishmentWithTradeDetails =>
        val strippedTaxIdentifier = convertTaxIdentifierForTransfer(euRegistrationWithoutFEWithTaxDetails.taxIdentifier.value, euRegistrationWithoutFEWithTaxDetails.country.code)
        extractFormattedTaxId(strippedTaxIdentifier, euRegistrationWithoutFEWithTaxDetails.taxIdentifier.identifierType, euRegistrationWithoutFEWithTaxDetails.country.code)
      case _ =>
        logger.info("not sending tax id for no fixed establishment")
        None
    }
  }

  private def convertTaxIdentifierForTransfer(identifier: String, countryCode: String): String = {

    CountryWithValidationDetails.euCountriesWithVRNValidationRules.find(_.country.code == countryCode) match {
      case Some(countryValidationDetails) =>
        if(identifier.matches(countryValidationDetails.vrnRegex)) {
          identifier.substring(2)
        } else {
          identifier
        }

      case _ =>
        logger.error("Error occurred while getting country code regex, unable to convert identifier")
        throw new IllegalStateException("Error occurred while getting country code regex, unable to convert identifier")
    }
  }

  private def toCoreCorrections(correctionsForPeriods: List[PeriodWithCorrections]): Map[Country, List[CoreCorrection]] = {
    val allCountriesWithCorrections = correctionsForPeriods.flatMap(_.correctionsToCountry.map(_.correctionCountry)).distinct

    allCountriesWithCorrections.map { countryWithCorrection =>

      val correctionsForCountry = for {
        correctionsForPeriod <- correctionsForPeriods.filter(_.correctionsToCountry.exists(_.correctionCountry == countryWithCorrection))
      } yield {
        val correctionsToCountry = correctionsForPeriod.correctionsToCountry.filter(_.correctionCountry == countryWithCorrection)
        val total = correctionsToCountry.map(_.countryVatCorrection).sum

        CoreCorrection(
          toCorePeriod(correctionsForPeriod.correctionReturnPeriod),
          total
        )
      }
      countryWithCorrection -> correctionsForCountry
    }.toMap
  }

  private def toCorePeriod(period: Period): CorePeriod = {
    CorePeriod(
      period.year,
      period.quarter.toString.tail.toInt
    )
  }

  private def extractFormattedTaxId(taxIdValue: String, taxIdType: models.domain.EuTaxIdentifierType, countryCode: String): Option[CoreEuTraderId] = {
    logger.info(s"sending vrn for fixed establishment for ${countryCode}")

    val formattedTaxIdValue =
      if (taxIdValue.startsWith(countryCode)) {
        countryCode match {
          case "FR" => if (taxIdValue.length == 13) {
            logger.info(s"Stripping country code for ${countryCode}")
            taxIdValue.substring(countryCode.length)
          } else {
            taxIdValue
          }
          case "NL" => if (taxIdValue.length == 14) {
            logger.info(s"Stripping country code for ${countryCode}")
            taxIdValue.substring(countryCode.length)
          } else {
            taxIdValue
          }
          case _ =>
            logger.info(s"Stripping country code for ${countryCode}")
            taxIdValue.substring(countryCode.length)
        }
      } else {
        taxIdValue
      }

    if (taxIdType.equals(Vat)) {
      Some(CoreEuTraderVatId(formattedTaxIdValue, countryCode))
    } else {
      logger.info(s"Sending tax id for fixed establishment for ${countryCode}")
      Some(CoreEuTraderTaxId(taxIdValue, countryCode))
    }
  }


}
