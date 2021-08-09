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
import models.requests.VatReturnRequest
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, Gen}
import uk.gov.hmrc.domain.Vrn

import java.time.Instant
import scala.math.BigDecimal.RoundingMode

trait ModelGenerators {
  self: Generators =>

  implicit val arbitraryPeriod: Arbitrary[Period] =
    Arbitrary {
      for {
        year    <- Gen.choose(2022, 2100)
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
        value          <- Gen.nonEmptyListOf(Gen.alphaChar).map(_.mkString)
      } yield EuTaxIdentifier(identifierType, value)
    }

  implicit val arbitraryVatRate: Arbitrary[VatRate] =
    Arbitrary {
      for {
        vatRateType <- Gen.oneOf(VatRateType.values)
        rate        <- Gen.choose(BigDecimal(0), BigDecimal(100))
      } yield VatRate(rate.setScale(2, RoundingMode.HALF_EVEN), vatRateType)
    }

    implicit val arbitrarySalesAtVatRate: Arbitrary[SalesAtVatRate] =
      Arbitrary {
        for {
          vatRate       <- arbitrary[VatRate]
          taxableAmount <- Gen.choose(BigDecimal(0), BigDecimal(1000000))
          vatAmount     <- Gen.choose(BigDecimal(0), BigDecimal(1000000))
        } yield SalesAtVatRate(
            vatRate,
            taxableAmount.setScale(2, RoundingMode.HALF_EVEN),
            vatAmount.setScale(2, RoundingMode.HALF_EVEN)
          )
      }

  implicit val arbitrarySalesToCountry: Arbitrary[SalesToCountry] =
    Arbitrary {
      for {
        country <- arbitrary[Country]
        number  <- Gen.choose(1, 3)
        amounts <- Gen.listOfN(number, arbitrary[SalesAtVatRate]).map(_.toSet)
      } yield SalesToCountry(country, amounts)
    }

  implicit val arbitrarySalesFromEuCountry: Arbitrary[SalesFromEuCountry] =
    Arbitrary {
      for {
        country       <- arbitrary[Country]
        taxIdentifier <- arbitrary[EuTaxIdentifier]
        number        <- Gen.choose(1, 3)
        amounts       <- Gen.listOfN(number, arbitrary[SalesToCountry]).map(_.toSet)
      } yield SalesFromEuCountry(country, taxIdentifier, amounts)
    }

  implicit val arbitraryVatReturn: Arbitrary[VatReturn] =
    Arbitrary {
      for {
        vrn         <- arbitrary[Vrn]
        period      <- arbitrary[Period]
        salesFromNi <- Gen.listOf(arbitrary[SalesToCountry]).map(_.toSet)
        salesFromEu <- Gen.listOf(arbitrary[SalesFromEuCountry]).map(_.toSet)
        now         = Instant.now
      } yield VatReturn(vrn, period, ReturnReference(vrn, period), None, None, salesFromNi, salesFromEu, now, now)
    }

  implicit val arbitraryVatReturnRequest: Arbitrary[VatReturnRequest] =
    Arbitrary {
      for {
        vrn         <- arbitrary[Vrn]
        period      <- arbitrary[Period]
        salesFromNi <- Gen.listOf(arbitrary[SalesToCountry]).map(_.toSet)
        salesFromEu <- Gen.listOf(arbitrary[SalesFromEuCountry]).map(_.toSet)
      } yield VatReturnRequest(vrn, period, None, None, salesFromNi, salesFromEu)
    }
}
