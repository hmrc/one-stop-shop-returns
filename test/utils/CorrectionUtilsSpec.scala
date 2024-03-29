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

package utils

import base.SpecBase
import models.{Country, CountryAmounts, Period, SalesDetails, SalesToCountry, StandardPeriod, VatOnSales, VatRate, VatRateType}
import models.VatOnSalesChoice.Standard
import models.corrections.{CorrectionPayload, CorrectionToCountry, PeriodWithCorrections}
import models.Quarter.{Q3, Q4}
import org.scalacheck.Arbitrary.arbitrary
import uk.gov.hmrc.domain.Vrn

import java.time.Instant

class CorrectionUtilsSpec extends SpecBase {

  "groupByCountry" - {

    "should add multiple countries with corrections and vat return" in {

      val country1 = arbitrary[Country].sample.value

      val correctionPayload = CorrectionPayload(
        vrn = completeVatReturn.vrn,
        period = completeVatReturn.period,
        corrections = List(
          PeriodWithCorrections(
            correctionReturnPeriod = StandardPeriod(2021, Q3),
            correctionsToCountry = List(
              CorrectionToCountry(country1, BigDecimal(10))
            )),
          PeriodWithCorrections(
            correctionReturnPeriod = StandardPeriod(2021, Q4),
            correctionsToCountry = List(
              CorrectionToCountry(country1, BigDecimal(10))
            ))
        ),
        submissionReceived = Instant.now(),
        lastUpdated = Instant.now()
      )

      val vatReturn = completeVatReturn.copy(
        salesFromNi = List(SalesToCountry(country1,
          List(SalesDetails(VatRate(10.00,
            VatRateType.Reduced),
            1000.00,
            VatOnSales(Standard, 100.00))))),
        salesFromEu = List.empty
      )

      CorrectionUtils.groupByCountryAndSum(correctionPayload, vatReturn) mustBe Map(country1 -> CountryAmounts(100, 0, 20))
    }

    "should add multiple countries with negative corrections" in {

      val country1 = arbitrary[Country].sample.value
      val country2 = arbitrary[Country].retryUntil(_ != country1).sample.value

      val correctionPayload = CorrectionPayload(
        vrn = arbitrary[Vrn].sample.value,
        period = arbitrary[Period].sample.value,
        corrections = List(
          PeriodWithCorrections(
            correctionReturnPeriod = StandardPeriod(2021, Q3),
            correctionsToCountry = List(
              CorrectionToCountry(country1, BigDecimal(10)),
              CorrectionToCountry(country2, BigDecimal(-10))
            )),
          PeriodWithCorrections(
            correctionReturnPeriod = StandardPeriod(2021, Q4),
            correctionsToCountry = List(
              CorrectionToCountry(country1, BigDecimal(-10)),
              CorrectionToCountry(country2, BigDecimal(10))
            ))
        ),
        submissionReceived = Instant.now(),
        lastUpdated = Instant.now()
      )

      val vatReturn = completeVatReturn.copy(
        salesFromNi = List(SalesToCountry(country1,
          List(SalesDetails(VatRate(10.00,
            VatRateType.Reduced),
            1000.00,
            VatOnSales(Standard, 100.00))))),
        salesFromEu = List.empty
      )

      CorrectionUtils.groupByCountryAndSum(correctionPayload, vatReturn) must contain.theSameElementsAs(
        Map(
          country1 -> CountryAmounts(100, 0, 0),
          country2 -> CountryAmounts(0, 0, 0)
        )
      )
    }

    "should have nil return with with a mix of corrections" in {

      val country1 = arbitrary[Country].sample.value
      val country2 = arbitrary[Country].retryUntil(_ != country1).sample.value

      val correctionPayload = CorrectionPayload(
        vrn = arbitrary[Vrn].sample.value,
        period = arbitrary[Period].sample.value,
        corrections = List(
          PeriodWithCorrections(
            correctionReturnPeriod = StandardPeriod(2021, Q3),
            correctionsToCountry = List(
              CorrectionToCountry(country1, BigDecimal(52.44)),
              CorrectionToCountry(country2, BigDecimal(-589.24))
            )),
          PeriodWithCorrections(
            correctionReturnPeriod = StandardPeriod(2021, Q4),
            correctionsToCountry = List(
              CorrectionToCountry(country1, BigDecimal(-10)),
            ))
        ),
        submissionReceived = Instant.now(),
        lastUpdated = Instant.now()
      )

      val vatReturn = emptyVatReturn

      CorrectionUtils.groupByCountryAndSum(correctionPayload, vatReturn) must contain.theSameElementsAs(
        Map(
          country1 -> CountryAmounts(0, 0, 42.44),
          country2 -> CountryAmounts(0, 0, -589.24)
        )
      )
    }

  }

}
