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

package services

import models._
import models.core._
import models.corrections.{CorrectionPayload, PeriodWithCorrections}
import utils.CorrectionUtils

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class CoreVatReturnService @Inject()(
                                      vatReturnSalesService: VatReturnSalesService
                                    )
                                    (implicit ec: ExecutionContext) {


  def toCore(vatReturn: VatReturn, correctionPayload: CorrectionPayload): CoreVatReturn = {
    val totalVatDue = vatReturnSalesService.getTotalVatOnSalesAfterCorrection(vatReturn, Some(correctionPayload))
    val amountsToCountries = CorrectionUtils.groupByCountryAndSum(correctionPayload, vatReturn)

    CoreVatReturn(
      vatReturnReferenceNumber = vatReturn.reference.value,
      version = vatReturn.lastUpdated.toString,
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
      msconSupplies = toCoreMsconSupplies(vatReturn.salesFromNi, vatReturn.salesFromEu, correctionPayload.corrections, amountsToCountries)
    )
  }

  private def toCoreMsconSupplies(salesFromNi: List[SalesToCountry], salesFromEu: List[SalesFromEuCountry], corrections: List[PeriodWithCorrections], amountsToCountries: Map[Country, CountryAmounts]): List[CoreMsconSupply] = {
    val listOfCountriesSoldTo = (
      salesFromNi.map(_.countryOfConsumption)
        ++ salesFromEu.flatMap(_.sales.map(_.countryOfConsumption))
        ++ corrections.flatMap(_.correctionsToCountry.map(_.correctionCountry))
      ).distinct

    val listOfSalesFromNiAsCoreSupplies = toCoreSupplies(salesFromNi)
    val listOfSalesFromEuAsCoreMsestSupplies = toCoreMsestSupplies(salesFromEu)
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

  private def toCoreMsestSupplies(salesFromEuCountry: List[SalesFromEuCountry]): Map[Country, List[CoreMsestSupply]] = {
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
        CoreMsestSupply(
          CoreEuTraderId(
            "", // TODO
            countrySoldFrom.code
          ),
          sales
        )
      }

      soldToEuCountry -> coreMsestSuppliesForCountry.toList
    }.toMap

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


}
