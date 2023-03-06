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

package utils

import models.{Country, CountryAmounts, VatReturn}
import models.corrections.CorrectionPayload

object CorrectionUtils {

  def groupByCountryAndSum(correctionPayload: CorrectionPayload, vatReturn: VatReturn): Map[Country, CountryAmounts] = {

    val correctionsToAllCountries = for {
      correctionPeriods <- correctionPayload.corrections
      correctionToCountry <- correctionPeriods.correctionsToCountry
    } yield correctionToCountry

    val correctionAmountsToAllCountries = correctionsToAllCountries.groupBy(_.correctionCountry).flatMap {
      case (country, corrections) =>
        val total = corrections.map(_.countryVatCorrection).sum

        Map(country -> CountryAmounts(0, 0, total))
    }

    correctionAmountsToAllCountries ++ groupByCountryAndSum(vatReturn).map {
      case (country, amount) =>
        val correctionTotalAmount = correctionAmountsToAllCountries.getOrElse(country, CountryAmounts(0, 0, 0)).totalVatFromCorrection
        country -> amount.copy(totalVatFromCorrection = correctionTotalAmount)
    }
  }

  private def groupByCountryAndSum(vatReturn: VatReturn): Map[Country, CountryAmounts] = {
    val returnAmountsToAllCountriesFromNi = (for {
      salesFromNi <- vatReturn.salesFromNi
    } yield {
      val totalAmount = salesFromNi.amounts.map(_.vatOnSales.amount).sum
      Map(salesFromNi.countryOfConsumption -> CountryAmounts(totalAmount, 0, 0))
    }).flatten.toMap

    val returnAmountsToAllCountriesFromEu = vatReturn.salesFromEu.flatMap(_.sales).groupBy(_.countryOfConsumption).flatMap {
      case (country, salesToCountry) => {
        val totalAmount = salesToCountry.flatMap(_.amounts.map(_.vatOnSales.amount)).sum

        Map(country -> CountryAmounts(0, totalAmount, 0))
      }
    }

    returnAmountsToAllCountriesFromNi ++ returnAmountsToAllCountriesFromEu.map {
      case (country, countryAmounts) =>
        val totalVatFromNi = returnAmountsToAllCountriesFromNi.getOrElse(country, CountryAmounts(0, 0, 0)).totalVatFromNi
        country -> countryAmounts.copy(totalVatFromNi = totalVatFromNi)
    }
  }

}