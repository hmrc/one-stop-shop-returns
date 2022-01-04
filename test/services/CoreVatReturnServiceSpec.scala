package services

import base.SpecBase
import models.corrections.{CorrectionPayload, CorrectionToCountry, PeriodWithCorrections}
import models.core.{CoreCorrection, CoreEuTraderId, CoreMsconSupply, CoreMsestSupply, CorePeriod, CoreSupply, CoreTraderId, CoreVatReturn}
import models.{Country, PaymentReference, Period, Quarter, ReturnReference, SalesDetails, SalesFromEuCountry, SalesToCountry, VatReturn}
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.BeforeAndAfterEach

import scala.concurrent.ExecutionContext.Implicits.global
import java.time.Instant
import scala.math.BigDecimal.RoundingMode

class CoreVatReturnServiceSpec extends SpecBase with BeforeAndAfterEach {

  private val vatReturnSalesService = mock[VatReturnSalesService]
  private val service = new CoreVatReturnService(vatReturnSalesService)

  override def beforeEach(): Unit = {
    Mockito.reset(vatReturnSalesService)
  }

  "CoreVatReturnService#toCore" - {

    val country1 = arbitrary[Country].sample.value
    val country2 = arbitrary[Country].retryUntil(_ != country1).sample.value
    val country3 = arbitrary[Country].retryUntil(c3 => c3 != country1 && c3 != country2).sample.value
    val salesDetails1 = arbitrary[SalesDetails].sample.value
    val salesDetails2 = arbitrary[SalesDetails].sample.value
    val salesDetails3 = arbitrary[SalesDetails].sample.value
    val returnReference = ReturnReference(vrn, period)
    val now = Instant.now()
    val vatReturn = VatReturn(
      vrn = vrn,
      period = period,
      reference = returnReference,
      paymentReference = PaymentReference(vrn, period),
      startDate = Some(period.firstDay),
      endDate = Some(period.lastDay),
      salesFromNi = List(
        SalesToCountry(
          country1,
          List(
            salesDetails1
          )
        )
      ),
      salesFromEu = List(
        SalesFromEuCountry(
          country2,
          None,
          List(
            SalesToCountry(
              country1,
              List(salesDetails2)
            ),
            SalesToCountry(
              country3,
              List(salesDetails3)
            )
          )
        )
      ),
      submissionReceived = now,
      lastUpdated = now
    )

    "convert from VatReturn to CoreVatReturn" in {

      val correctionPayload = CorrectionPayload(vrn, period, List.empty, now, now)

      val expectedTotal = salesDetails1.vatOnSales.amount + salesDetails2.vatOnSales.amount + salesDetails3.vatOnSales.amount

      when(vatReturnSalesService.getTotalVatOnSalesAfterCorrection(vatReturn, Some(correctionPayload))).thenReturn(
        expectedTotal
      )

      val expectedResultCoreVatReturn = CoreVatReturn(
        vatReturnReferenceNumber = returnReference.value,
        version = vatReturn.lastUpdated.toString,
        traderId = CoreTraderId(vrn.vrn, "XI"),
        period = CorePeriod(period.year, period.quarter.toString.tail.toInt),
        startDate = period.firstDay,
        endDate = period.lastDay,
        submissionDateTime = now,
        totalAmountVatDueGBP = expectedTotal,
        msconSupplies = List(
          CoreMsconSupply(
            msconCountryCode = country1.code,
            balanceOfVatDueGBP = salesDetails1.vatOnSales.amount + salesDetails2.vatOnSales.amount,
            grandTotalMsidGoodsGBP = salesDetails1.vatOnSales.amount,
            grandTotalMsestGoodsGBP = salesDetails2.vatOnSales.amount,
            correctionsTotalGBP = BigDecimal(0),
            msidSupplies = List(
              CoreSupply(
                supplyType = "GOODS",
                vatRate = salesDetails1.vatRate.rate,
                vatRateType = salesDetails1.vatRate.rateType.toString,
                taxableAmountGBP = salesDetails1.netValueOfSales,
                vatAmountGBP = salesDetails1.vatOnSales.amount
              )
            ),
            msestSupplies = List(
              CoreMsestSupply(
                CoreEuTraderId("", country2.code),
                List(CoreSupply(
                  supplyType = "GOODS",
                  vatRate = salesDetails2.vatRate.rate,
                  vatRateType = salesDetails2.vatRate.rateType.toString,
                  taxableAmountGBP = salesDetails2.netValueOfSales,
                  vatAmountGBP = salesDetails2.vatOnSales.amount
                ))
              )
            ),
            corrections = List.empty
          ),
          CoreMsconSupply(
            msconCountryCode = country3.code,
            balanceOfVatDueGBP = salesDetails3.vatOnSales.amount,
            grandTotalMsidGoodsGBP = BigDecimal(0),
            grandTotalMsestGoodsGBP = salesDetails3.vatOnSales.amount,
            correctionsTotalGBP = BigDecimal(0),
            msidSupplies = List.empty,
            msestSupplies = List(
              CoreMsestSupply(
                CoreEuTraderId("", country2.code),
                List(CoreSupply(
                  supplyType = "GOODS",
                  vatRate = salesDetails3.vatRate.rate,
                  vatRateType = salesDetails3.vatRate.rateType.toString,
                  taxableAmountGBP = salesDetails3.netValueOfSales,
                  vatAmountGBP = salesDetails3.vatOnSales.amount
                ))
              )
            ),
            corrections = List.empty
          )
        )
      )

      val result = service.toCore(vatReturn, correctionPayload)

      result mustBe expectedResultCoreVatReturn
    }

    "convert from VatReturn and correctionPayload to CoreVatReturn" in {

      val correctionPeriod1 = Period(2021, Quarter.Q1)
      val correctionPeriod2 = Period(2021, Quarter.Q2)

      val correctionAmount1 = -(salesDetails1.vatOnSales.amount / 2).setScale(2, RoundingMode.HALF_UP)
      val correctionAmount2 = BigDecimal(500)

      val correctionPayload = CorrectionPayload(vrn, period, List(
        PeriodWithCorrections(
          correctionReturnPeriod = correctionPeriod1,
          correctionsToCountry = List(
            CorrectionToCountry(country1, correctionAmount1)
          )
        ),
        PeriodWithCorrections(
          correctionReturnPeriod = correctionPeriod2,
          correctionsToCountry = List(
            CorrectionToCountry(country3, correctionAmount2)
          )
        )
      ), now, now)

      val expectedTotal = salesDetails1.vatOnSales.amount +
        salesDetails2.vatOnSales.amount +
        salesDetails3.vatOnSales.amount +
        correctionAmount1 +
        correctionAmount2

      when(vatReturnSalesService.getTotalVatOnSalesAfterCorrection(vatReturn, Some(correctionPayload))).thenReturn(
        expectedTotal
      )

      val expectedResultCoreVatReturn = CoreVatReturn(
        vatReturnReferenceNumber = returnReference.value,
        version = vatReturn.lastUpdated.toString,
        traderId = CoreTraderId(vrn.vrn, "XI"),
        period = CorePeriod(period.year, period.quarter.toString.tail.toInt),
        startDate = period.firstDay,
        endDate = period.lastDay,
        submissionDateTime = now,
        totalAmountVatDueGBP = expectedTotal,
        msconSupplies = List(
          CoreMsconSupply(
            msconCountryCode = country1.code,
            balanceOfVatDueGBP = salesDetails1.vatOnSales.amount + salesDetails2.vatOnSales.amount + correctionAmount1,
            grandTotalMsidGoodsGBP = salesDetails1.vatOnSales.amount,
            grandTotalMsestGoodsGBP = salesDetails2.vatOnSales.amount,
            correctionsTotalGBP = correctionAmount1,
            msidSupplies = List(
              CoreSupply(
                supplyType = "GOODS",
                vatRate = salesDetails1.vatRate.rate,
                vatRateType = salesDetails1.vatRate.rateType.toString,
                taxableAmountGBP = salesDetails1.netValueOfSales,
                vatAmountGBP = salesDetails1.vatOnSales.amount
              )
            ),
            msestSupplies = List(
              CoreMsestSupply(
                CoreEuTraderId("", country2.code),
                List(CoreSupply(
                  supplyType = "GOODS",
                  vatRate = salesDetails2.vatRate.rate,
                  vatRateType = salesDetails2.vatRate.rateType.toString,
                  taxableAmountGBP = salesDetails2.netValueOfSales,
                  vatAmountGBP = salesDetails2.vatOnSales.amount
                ))
              )
            ),
            corrections = List(
              CoreCorrection(
                period = CorePeriod(correctionPeriod1.year, correctionPeriod1.quarter.toString.tail.toInt),
                totalVatAmountCorrectionGBP = correctionAmount1
              )
            )
          ),
          CoreMsconSupply(
            msconCountryCode = country3.code,
            balanceOfVatDueGBP = salesDetails3.vatOnSales.amount + correctionAmount2,
            grandTotalMsidGoodsGBP = BigDecimal(0),
            grandTotalMsestGoodsGBP = salesDetails3.vatOnSales.amount,
            correctionsTotalGBP = correctionAmount2,
            msidSupplies = List.empty,
            msestSupplies = List(
              CoreMsestSupply(
                CoreEuTraderId("", country2.code),
                List(CoreSupply(
                  supplyType = "GOODS",
                  vatRate = salesDetails3.vatRate.rate,
                  vatRateType = salesDetails3.vatRate.rateType.toString,
                  taxableAmountGBP = salesDetails3.netValueOfSales,
                  vatAmountGBP = salesDetails3.vatOnSales.amount
                ))
              )
            ),
            corrections = List(
              CoreCorrection(
                period = CorePeriod(correctionPeriod2.year, correctionPeriod2.quarter.toString.tail.toInt),
                totalVatAmountCorrectionGBP = correctionAmount2
              )
            )
          )
        )
      )


      service.toCore(vatReturn, correctionPayload) mustBe expectedResultCoreVatReturn
    }
  }

}
