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

package generators

import models._
import models.corrections.{PeriodWithCorrections, CorrectionPayload, CorrectionToCountry}
import models.financialdata.Charge
import models.requests.{CorrectionRequest, VatReturnRequest}
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Arbitrary.arbitrary
import uk.gov.hmrc.domain.Vrn

import java.time.Instant
import scala.math.BigDecimal.RoundingMode

trait ModelGenerators {
  self: Generators =>

  implicit val arbitraryPeriod: Arbitrary[Period] =
    Arbitrary {
      for {
        year <- Gen.choose(2022, 2099)
        quarter <- arbitrary[Quarter]
      } yield Period(year, quarter)
    }

  implicit val arbitraryVrn: Arbitrary[Vrn] =
    Arbitrary {
      Gen.listOfN(9, Gen.numChar).map(_.mkString).map(Vrn)
    }

  implicit val arbitraryQuarter: Arbitrary[Quarter] =
    Arbitrary {
      Gen.oneOf(Quarter.values)
    }

  implicit val arbitraryCorrectionToCountry: Arbitrary[CorrectionToCountry] =
    Arbitrary {
      for {
        country <- arbitrary[Country]
        countryVatCorrection <- Gen.choose(BigDecimal(1), BigDecimal(999999))
      } yield CorrectionToCountry(country, countryVatCorrection.setScale(2, RoundingMode.HALF_UP))
    }

  implicit val arbitraryPeriodWithCorrections: Arbitrary[PeriodWithCorrections] =
    Arbitrary {
      for {
        correctionPeriod <- arbitrary[Period]
        amount <- Gen.choose(1, 30)
        correctionToCountries <- Gen.listOfN(amount, arbitrary[CorrectionToCountry])
      } yield PeriodWithCorrections(correctionPeriod, correctionToCountries)
    }

  implicit val arbitraryCorrectionPayload: Arbitrary[CorrectionPayload] =
    Arbitrary {
      for {
        vrn <- arbitrary[Vrn]
        period <- arbitrary[Period]
        amount <- Gen.choose(1, 30)
        corrections <- Gen.listOfN(amount, arbitrary[PeriodWithCorrections])
        now = Instant.now
      } yield CorrectionPayload(vrn, period, corrections, now, now)
    }

  implicit val arbitraryCorrectionRequest: Arbitrary[CorrectionRequest] =
    Arbitrary {
      for {
        vrn <- arbitrary[Vrn]
        period <- arbitrary[Period]
        amount <- Gen.choose(1, 30)
        corrections <- Gen.listOfN(amount, arbitrary[PeriodWithCorrections])
      } yield CorrectionRequest(vrn, period, corrections)
    }

  implicit val arbitraryCountry: Arbitrary[Country] =
    Arbitrary {
      for {
        code <- Gen.listOfN(2, Gen.alphaUpperChar).map(_.mkString)
        name <- Gen.nonEmptyListOf(Gen.alphaChar).map(_.mkString)
      } yield Country(code, name)
    }

  implicit val arbitraryEuTaxIdentifier: Arbitrary[EuTaxIdentifier] =
    Arbitrary {
      for {
        identifierType <- Gen.oneOf(EuTaxIdentifierType.values)
        value <- Gen.nonEmptyListOf(Gen.alphaChar).map(_.mkString)
      } yield EuTaxIdentifier(identifierType, value)
    }

  implicit val arbitraryVatRate: Arbitrary[VatRate] =
    Arbitrary {
      for {
        vatRateType <- Gen.oneOf(VatRateType.values)
        rate <- Gen.choose(BigDecimal(0), BigDecimal(100))
      } yield VatRate(rate.setScale(2, RoundingMode.HALF_UP), vatRateType)
    }

  implicit val arbitraryVatOnSales: Arbitrary[VatOnSales] =
    Arbitrary {
      for {
        choice <- Gen.oneOf(VatOnSalesChoice.values)
        amount <- Gen.choose[BigDecimal](0, 1000000)
      } yield VatOnSales(choice, amount.setScale(2, RoundingMode.HALF_UP))
    }

  implicit val arbitrarySalesAtVatRate: Arbitrary[SalesDetails] =
    Arbitrary {
      for {
        vatRate <- arbitrary[VatRate]
        taxableAmount <- Gen.choose(BigDecimal(0), BigDecimal(1000000))
        vatOnSales <- arbitrary[VatOnSales]
      } yield SalesDetails(
        vatRate,
        taxableAmount.setScale(2, RoundingMode.HALF_UP),
        vatOnSales
      )
    }

  implicit val arbitrarySalesToCountry: Arbitrary[SalesToCountry] =
    Arbitrary {
      for {
        country <- arbitrary[Country]
        number <- Gen.choose(1, 2)
        amounts <- Gen.listOfN(number, arbitrary[SalesDetails])
      } yield SalesToCountry(country, amounts)
    }

  implicit val arbitrarySalesFromEuCountry: Arbitrary[SalesFromEuCountry] =
    Arbitrary {
      for {
        country <- arbitrary[Country]
        taxIdentifier <- Gen.option(arbitrary[EuTaxIdentifier])
        number <- Gen.choose(1, 3)
        amounts <- Gen.listOfN(number, arbitrary[SalesToCountry])
      } yield SalesFromEuCountry(country, taxIdentifier, amounts)
    }

  implicit val arbitraryVatReturn: Arbitrary[VatReturn] =
    Arbitrary {
      for {
        vrn <- arbitrary[Vrn]
        period <- arbitrary[Period]
        niSales <- Gen.choose(1, 3)
        euSales <- Gen.choose(1, 3)
        salesFromNi <- Gen.listOfN(niSales, arbitrary[SalesToCountry])
        salesFromEu <- Gen.listOfN(euSales, arbitrary[SalesFromEuCountry])
        now = Instant.now
      } yield VatReturn(
        vrn, period, ReturnReference(vrn, period), PaymentReference(vrn, period), None, None, salesFromNi, salesFromEu, now, now)
    }

  implicit val arbitraryVatReturnRequest: Arbitrary[VatReturnRequest] =
    Arbitrary {
      for {
        vrn <- arbitrary[Vrn]
        period <- arbitrary[Period]
        niSales <- Gen.choose(1, 3)
        euSales <- Gen.choose(1, 3)
        salesFromNi <- Gen.listOfN(niSales, arbitrary[SalesToCountry])
        salesFromEu <- Gen.listOfN(euSales, arbitrary[SalesFromEuCountry])
      } yield VatReturnRequest(vrn, period, None, None, salesFromNi, salesFromEu)
    }

  implicit val arbitraryCharge: Arbitrary[Charge] =
    Arbitrary {
      for {
        period <- arbitrary[Period]
        originalAmount <- arbitrary[BigDecimal]
        outstandingAmount <- arbitrary[BigDecimal]
        clearedAmount <- arbitrary[BigDecimal]
      } yield Charge(period, originalAmount, outstandingAmount, clearedAmount)
    }
}
