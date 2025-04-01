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

import models.*
import models.Period.toEtmpPeriodString
import models.corrections.{CorrectionPayload, CorrectionToCountry, PeriodWithCorrections, ReturnCorrectionValue}
import models.etmp.*
import models.exclusions.{ExcludedTrader, ExclusionReason}
import models.financialdata.Charge
import models.requests.{CorrectionRequest, SaveForLaterRequest, VatReturnRequest, VatReturnWithCorrectionRequest}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, Gen}
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.domain.Vrn

import java.math.MathContext
import java.time.{Instant, LocalDate, LocalDateTime}
import scala.math.BigDecimal.RoundingMode

trait ModelGenerators {
  self: Generators =>

  implicit val arbitraryBigDecimal: Arbitrary[BigDecimal] = {
    Arbitrary {
      for {
        nonDecimalNumber <- arbitrary[Int].retryUntil(_ > 0)
        decimalNumber <- arbitrary[Int].retryUntil(_ > 0).retryUntil(_.toString.reverse.head.toString != "0")
      } yield BigDecimal(s"$nonDecimalNumber.$decimalNumber")
    }
  }

  implicit val arbitraryPeriod: Arbitrary[Period] = {
    Arbitrary {
      for {
        year <- Gen.choose(2022, 2099)
        quarter <- arbitrary[Quarter]
      } yield StandardPeriod(year, quarter)
    }
  }

  implicit val arbitraryVrn: Arbitrary[Vrn] = {
    Arbitrary {
      Gen.listOfN(9, Gen.numChar).map(_.mkString).map(Vrn)
    }
  }

  implicit val arbitraryQuarter: Arbitrary[Quarter] = {
    Arbitrary {
      Gen.oneOf(Quarter.values)
    }
  }

  implicit val arbitraryCorrectionToCountry: Arbitrary[CorrectionToCountry] = {
    Arbitrary {
      for {
        country <- arbitrary[Country]
        countryVatCorrection <- Gen.choose(BigDecimal(1), BigDecimal(999999))
      } yield CorrectionToCountry(country, countryVatCorrection.setScale(2, RoundingMode.HALF_UP))
    }
  }

  implicit val arbitraryPeriodWithCorrections: Arbitrary[PeriodWithCorrections] = {
    Arbitrary {
      for {
        correctionPeriod <- arbitrary[Period]
        amount <- Gen.choose(1, 30)
        correctionsToCountry <- Gen.listOfN(amount, arbitrary[CorrectionToCountry])
      } yield PeriodWithCorrections(correctionPeriod, correctionsToCountry)
    }
  }

  implicit val arbitraryCorrectionPayload: Arbitrary[CorrectionPayload] = {
    Arbitrary {
      for {
        vrn <- arbitrary[Vrn]
        period <- arbitrary[Period]
        amount <- Gen.choose(1, 30)
        corrections <- Gen.listOfN(amount, arbitrary[PeriodWithCorrections])
        now = Instant.now
      } yield CorrectionPayload(vrn, period, corrections, now, now)
    }
  }

  implicit val arbitraryCorrectionRequest: Arbitrary[CorrectionRequest] = {
    Arbitrary {
      for {
        vrn <- arbitrary[Vrn]
        period <- arbitrary[Period]
        amount <- Gen.choose(1, 30)
        corrections <- Gen.listOfN(amount, arbitrary[PeriodWithCorrections])
      } yield CorrectionRequest(vrn, period, corrections)
    }
  }

  implicit val arbitraryVatReturnWithCorrectionRequest: Arbitrary[VatReturnWithCorrectionRequest] = {
    Arbitrary {
      for {
        vatReturnRequest <- arbitrary[VatReturnRequest]
        correctionRequest <- arbitrary[CorrectionRequest]
      } yield VatReturnWithCorrectionRequest(vatReturnRequest, correctionRequest)
    }
  }

  implicit val arbitraryCountry: Arbitrary[Country] = {
    Arbitrary {
      for {
        code <- Gen.listOfN(2, Gen.alphaUpperChar).map(_.mkString)
        name <- Gen.nonEmptyListOf(Gen.alphaChar).map(_.mkString)
      } yield Country(code, name)
    }
  }

  implicit val arbitraryEuTaxIdentifier: Arbitrary[EuTaxIdentifier] = {
    Arbitrary {
      for {
        identifierType <- Gen.oneOf(EuTaxIdentifierType.values)
        value <- Gen.nonEmptyListOf(Gen.alphaChar).map(_.mkString)
      } yield EuTaxIdentifier(identifierType, value)
    }
  }

  implicit val arbitraryVatRate: Arbitrary[VatRate] = {
    Arbitrary {
      for {
        vatRateType <- Gen.oneOf(VatRateType.values)
        rate <- Gen.choose(BigDecimal(0), BigDecimal(100))
      } yield VatRate(rate.setScale(2, RoundingMode.HALF_UP), vatRateType)
    }
  }

  implicit val arbitraryVatOnSales: Arbitrary[VatOnSales] = {
    Arbitrary {
      for {
        choice <- Gen.oneOf(VatOnSalesChoice.values)
        amount <- Gen.choose[BigDecimal](0, 1000000)
      } yield VatOnSales(choice, amount.setScale(2, RoundingMode.HALF_UP))
    }
  }

  implicit val arbitrarySalesAtVatRate: Arbitrary[SalesDetails] = {
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
  }

  implicit val arbitrarySalesToCountry: Arbitrary[SalesToCountry] = {
    Arbitrary {
      for {
        country <- arbitrary[Country]
        number <- Gen.choose(1, 2)
        amounts <- Gen.listOfN(number, arbitrary[SalesDetails])
      } yield SalesToCountry(country, amounts)
    }
  }

  implicit val arbitrarySalesFromEuCountry: Arbitrary[SalesFromEuCountry] = {
    Arbitrary {
      for {
        country <- arbitrary[Country]
        taxIdentifier <- Gen.option(arbitrary[EuTaxIdentifier])
        number <- Gen.choose(1, 3)
        amounts <- Gen.listOfN(number, arbitrary[SalesToCountry])
      } yield SalesFromEuCountry(country, taxIdentifier, amounts)
    }
  }

  implicit val arbitraryVatReturn: Arbitrary[VatReturn] = {
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
  }

  implicit val arbitraryVatReturnRequest: Arbitrary[VatReturnRequest] = {
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
  }

  implicit val arbitraryCharge: Arbitrary[Charge] = {
    Arbitrary {
      for {
        period <- arbitrary[Period]
        originalAmount <- arbitrary[BigDecimal]
        outstandingAmount <- arbitrary[BigDecimal]
        clearedAmount <- arbitrary[BigDecimal]
      } yield Charge(period, originalAmount, outstandingAmount, clearedAmount)
    }
  }

  implicit val arbitrarySavedUserAnswers: Arbitrary[SavedUserAnswers] = {
    Arbitrary {
      for {
        vrn <- arbitrary[Vrn]
        period <- arbitrary[Period]
        data = JsObject(Seq(
          "test" -> Json.toJson("test")
        ))
        now = Instant.now
      } yield SavedUserAnswers(
        vrn, period, data, now)
    }
  }

  implicit val arbitrarySaveForLaterRequest: Arbitrary[SaveForLaterRequest] = {
    Arbitrary {
      for {
        vrn <- arbitrary[Vrn]
        period <- arbitrary[Period]
        data = JsObject(Seq(
          "test" -> Json.toJson("test")
        ))
      } yield SaveForLaterRequest(vrn, period, data)
    }
  }

  implicit lazy val arbitraryDate: Arbitrary[LocalDate] = {
    Arbitrary {
      datesBetween(LocalDate.of(2021, 7, 1), LocalDate.of(2023, 12, 31))
    }
  }

  implicit lazy val arbitraryExcludedTrader: Arbitrary[ExcludedTrader] = {
    Arbitrary {
      for {
        vrn <- arbitraryVrn.arbitrary
        exclusionReason <- Gen.oneOf(ExclusionReason.values)
        effectiveDate <- arbitraryDate.arbitrary
      } yield ExcludedTrader(
        vrn = vrn,
        exclusionReason = exclusionReason,
        effectiveDate = effectiveDate
      )
    }
  }

  implicit val arbitraryReturnCorrectionValue: Arbitrary[ReturnCorrectionValue] = {
    Arbitrary {
      for {
        correctionValue <- arbitrary[BigDecimal]
      } yield ReturnCorrectionValue(correctionValue)
    }
  }

  implicit val arbitraryObligationDetails: Arbitrary[EtmpObligationDetails] = {
    Arbitrary {
      for {
        status <- Gen.oneOf(EtmpObligationsFulfilmentStatus.values)
        periodKey <- arbitrary[Period].map(toEtmpPeriodString)
      } yield EtmpObligationDetails(
        status = status,
        periodKey = periodKey
      )
    }
  }

  implicit val arbitraryObligations: Arbitrary[EtmpObligations] = {
    Arbitrary {
      for {
        referenceNumber <- arbitrary[String]
        obligationDetails <- Gen.listOfN(3, arbitrary[EtmpObligationDetails])
      } yield {
        EtmpObligations(obligations = Seq(EtmpObligation(
          obligationDetails = obligationDetails
        )))
      }
    }
  }

  implicit lazy val arbitraryEtmpVatReturnGoodsSupplied: Arbitrary[EtmpVatReturnGoodsSupplied] = {
    Arbitrary {
      for {
        msOfConsumption <- arbitrary[String]
        vatRateType <- Gen.oneOf(EtmpVatRateType.values)
        taxableAmountGBP <- arbitrary[BigDecimal]
        vatAmountGBP <- arbitrary[BigDecimal]
      } yield EtmpVatReturnGoodsSupplied(
        msOfConsumption = msOfConsumption,
        vatRateType = vatRateType,
        taxableAmountGBP = taxableAmountGBP,
        vatAmountGBP = vatAmountGBP
      )
    }
  }

  implicit lazy val arbitraryEtmpVatReturnGoodsDispatched: Arbitrary[EtmpVatReturnGoodsDispatched] = {
    Arbitrary {
      for {
        msOfEstablishment <- arbitrary[String]
        msOfConsumption <- arbitrary[String]
        vatRateType <- Gen.oneOf(EtmpVatRateType.values)
        taxableAmountGBP <- arbitrary[BigDecimal]
        vatAmountGBP <- arbitrary[BigDecimal]
      } yield EtmpVatReturnGoodsDispatched(
        msOfEstablishment = msOfEstablishment,
        msOfConsumption = msOfConsumption,
        vatRateType = vatRateType,
        taxableAmountGBP = taxableAmountGBP,
        vatAmountGBP = vatAmountGBP
      )
    }
  }

  implicit lazy val arbitraryEtmpVatReturnCorrection: Arbitrary[EtmpVatReturnCorrection] = {
    Arbitrary {
      for {
        periodKey <- arbitrary[String]
        periodFrom <- arbitrary[String]
        periodTo <- arbitrary[String]
        msOfConsumption <- arbitrary[String]
        totalVATAmountCorrectionGBP <- arbitrary[BigDecimal]
        totalVATAmountCorrectionEUR <- arbitrary[BigDecimal]
      } yield EtmpVatReturnCorrection(
        periodKey = periodKey,
        periodFrom = periodFrom,
        periodTo = periodTo,
        msOfConsumption = msOfConsumption,
        totalVATAmountCorrectionGBP = totalVATAmountCorrectionGBP,
        totalVATAmountCorrectionEUR = totalVATAmountCorrectionEUR
      )
    }
  }

  implicit lazy val arbitraryEtmpVatReturnBalanceOfVatDue: Arbitrary[EtmpVatReturnBalanceOfVatDue] = {
    Arbitrary {
      for {
        msOfConsumption <- arbitrary[String]
        totalVATDueGBP <- arbitrary[BigDecimal].retryUntil(x => x > 0 && x < 100000000).map(_.round(MathContext(4)))
        totalVATEUR <- arbitrary[BigDecimal]
      } yield EtmpVatReturnBalanceOfVatDue(
        msOfConsumption = msOfConsumption,
        totalVATDueGBP = totalVATDueGBP,
        totalVATEUR = totalVATEUR
      )
    }
  }

  implicit lazy val arbitraryEtmpVatReturn: Arbitrary[EtmpVatReturn] = {
    Arbitrary {
      for {
        returnReference <- arbitrary[String]
        returnVersion <- arbitrary[LocalDateTime]
        periodKey <- arbitrary[String]
        returnPeriodFrom <- arbitrary[LocalDate]
        returnPeriodTo <- arbitrary[LocalDate]
        amountOfGoodsSupplied <- Gen.oneOf(List(1, 2, 3))
        goodsSupplied <- Gen.listOfN(amountOfGoodsSupplied, arbitrary[EtmpVatReturnGoodsSupplied])
        totalVATGoodsSuppliedGBP <- arbitrary[BigDecimal]
        amountOfGoodsDispatched <- Gen.oneOf(List(1, 2, 3))
        goodsDispatched <- Gen.listOfN(amountOfGoodsDispatched, arbitrary[EtmpVatReturnGoodsDispatched])
        totalVatAmtDispatchedGBP <- arbitrary[BigDecimal]
        totalVATAmountPayable <- arbitrary[BigDecimal]
        totalVATAmountPayableAllSpplied <- arbitrary[BigDecimal]
        amountOfCorrections <- Gen.oneOf(List(1, 2, 3))
        correctionPreviousVATReturn <- Gen.listOfN(amountOfCorrections, arbitrary[EtmpVatReturnCorrection])
        totalVATAmountFromCorrectionGBP <- arbitrary[BigDecimal]
        amountOfBalanceOfVATDueForMS <- Gen.oneOf(List(1, 2, 3))
        balanceOfVATDueForMS <- Gen.listOfN(amountOfBalanceOfVATDueForMS, arbitrary[EtmpVatReturnBalanceOfVatDue])
        totalVATAmountDueForAllMSGBP <- arbitrary[BigDecimal].retryUntil(x => x > 0 && x < 100000000).map(_.round(MathContext(2)))
        paymentReference <- arbitrary[String]
      } yield EtmpVatReturn(
        returnReference = returnReference,
        returnVersion = returnVersion,
        periodKey = periodKey,
        returnPeriodFrom = returnPeriodFrom,
        returnPeriodTo = returnPeriodTo,
        goodsSupplied = goodsSupplied,
        totalVATGoodsSuppliedGBP = totalVATGoodsSuppliedGBP,
        goodsDispatched = goodsDispatched,
        totalVatAmtDispatchedGBP = totalVatAmtDispatchedGBP,
        totalVATAmountPayable = totalVATAmountPayable,
        totalVATAmountPayableAllSpplied = totalVATAmountPayableAllSpplied,
        correctionPreviousVATReturn = correctionPreviousVATReturn,
        totalVATAmountFromCorrectionGBP = totalVATAmountFromCorrectionGBP,
        balanceOfVATDueForMS = balanceOfVATDueForMS,
        totalVATAmountDueForAllMSGBP = totalVATAmountDueForAllMSGBP,
        paymentReference = paymentReference
      )
    }
  }
}
