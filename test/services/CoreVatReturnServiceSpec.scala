package services

import base.SpecBase
import connectors.RegistrationConnector
import models._
import models.core._
import models.corrections.{CorrectionPayload, CorrectionToCountry, PeriodWithCorrections}
import models.domain.{EuTaxIdentifier, EuTaxIdentifierType, _}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.{BeforeAndAfterEach, PrivateMethodTester}
import testutils.RegistrationData
import testutils.RegistrationData.registration
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.math.BigDecimal.RoundingMode

class CoreVatReturnServiceSpec extends SpecBase with BeforeAndAfterEach with PrivateMethodTester {

  private val vatReturnSalesService = mock[VatReturnSalesService]
  private val registrationConnector = mock[RegistrationConnector]
  private val service = new CoreVatReturnService(vatReturnSalesService, registrationConnector)
  implicit private lazy val hc: HeaderCarrier = HeaderCarrier()

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
    val salesDetails4 = arbitrary[SalesDetails].sample.value
    val salesDetails5 = arbitrary[SalesDetails].sample.value
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
    val vatReturn2 = VatReturn(
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
            salesDetails1,
            salesDetails4
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
              List(
                salesDetails3,
                salesDetails5
              )
            )
          )
        )
      ),
      submissionReceived = now,
      lastUpdated = now,

    )

    "convert from VatReturn to CoreVatReturn" - {

      val correctionPayload = CorrectionPayload(vrn, period, List.empty, now, now)

      val expectedTotal = salesDetails1.vatOnSales.amount + salesDetails2.vatOnSales.amount + salesDetails3.vatOnSales.amount

      val expectedTotal2 = salesDetails1.vatOnSales.amount + salesDetails2.vatOnSales.amount + salesDetails3.vatOnSales.amount + salesDetails4.vatOnSales.amount + salesDetails5.vatOnSales.amount

      val expectedResultCoreVatReturn = CoreVatReturn(
        vatReturnReferenceNumber = returnReference.value,
        version = vatReturn.lastUpdated.truncatedTo(ChronoUnit.MILLIS),
        traderId = CoreTraderId(vrn.vrn, "XI"),
        period = CorePeriod(period.year, period.quarter.toString.tail.toInt),
        startDate = period.firstDay,
        endDate = period.lastDay,
        submissionDateTime = now.truncatedTo(ChronoUnit.MILLIS),
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
                Some(country2.code),
                None,
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
                Some(country2.code),
                None,
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
        ),
        changeDate = registration.adminUse.changeDate
      )

      val expectedResultCoreVatReturn2 = CoreVatReturn(
        vatReturnReferenceNumber = returnReference.value,
        version = vatReturn.lastUpdated.truncatedTo(ChronoUnit.MILLIS),
        traderId = CoreTraderId(vrn.vrn, "XI"),
        period = CorePeriod(period.year, period.quarter.toString.tail.toInt),
        startDate = period.firstDay,
        endDate = period.lastDay,
        submissionDateTime = now.truncatedTo(ChronoUnit.MILLIS),
        totalAmountVatDueGBP = expectedTotal2,
        msconSupplies = List(
          CoreMsconSupply(
            msconCountryCode = country1.code,
            balanceOfVatDueGBP = salesDetails1.vatOnSales.amount + salesDetails4.vatOnSales.amount + salesDetails2.vatOnSales.amount,
            grandTotalMsidGoodsGBP = salesDetails1.vatOnSales.amount + salesDetails4.vatOnSales.amount,
            grandTotalMsestGoodsGBP = salesDetails2.vatOnSales.amount,
            correctionsTotalGBP = BigDecimal(0),
            msidSupplies = List(
              CoreSupply(
                supplyType = "GOODS",
                vatRate = salesDetails1.vatRate.rate,
                vatRateType = salesDetails1.vatRate.rateType.toString,
                taxableAmountGBP = salesDetails1.netValueOfSales,
                vatAmountGBP = salesDetails1.vatOnSales.amount
              ),
              CoreSupply(
                supplyType = "GOODS",
                vatRate = salesDetails4.vatRate.rate,
                vatRateType = salesDetails4.vatRate.rateType.toString,
                taxableAmountGBP = salesDetails4.netValueOfSales,
                vatAmountGBP = salesDetails4.vatOnSales.amount
              ),
            ),
            msestSupplies = List(
              CoreMsestSupply(
                Some(country2.code),
                None,
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
            balanceOfVatDueGBP = salesDetails3.vatOnSales.amount + salesDetails5.vatOnSales.amount,
            grandTotalMsidGoodsGBP = BigDecimal(0),
            grandTotalMsestGoodsGBP = salesDetails3.vatOnSales.amount + salesDetails5.vatOnSales.amount,
            correctionsTotalGBP = BigDecimal(0),
            msidSupplies = List.empty,
            msestSupplies = List(
              CoreMsestSupply(
                Some(country2.code),
                None,
                List(CoreSupply(
                  supplyType = "GOODS",
                  vatRate = salesDetails3.vatRate.rate,
                  vatRateType = salesDetails3.vatRate.rateType.toString,
                  taxableAmountGBP = salesDetails3.netValueOfSales,
                  vatAmountGBP = salesDetails3.vatOnSales.amount
                ),
                CoreSupply(
                  supplyType = "GOODS",
                  vatRate = salesDetails5.vatRate.rate,
                  vatRateType = salesDetails5.vatRate.rateType.toString,
                  taxableAmountGBP = salesDetails5.netValueOfSales,
                  vatAmountGBP = salesDetails5.vatOnSales.amount
                ))
              )
            ),
            corrections = List.empty
          )
        ),
        changeDate = registration.adminUse.changeDate
      )

      "successful when registration returns a registration" in {
        when(vatReturnSalesService.getTotalVatOnSalesAfterCorrection(vatReturn, Some(correctionPayload))).thenReturn(
          expectedTotal
        )
        when(registrationConnector.getRegistration()(any())) `thenReturn` Future.successful(Some(RegistrationData.registration))

        val result = service.toCore(vatReturn, correctionPayload).futureValue

        result mustBe expectedResultCoreVatReturn
      }

      "successful when more complex registration returns a registration" in {
        when(vatReturnSalesService.getTotalVatOnSalesAfterCorrection(vatReturn2, Some(correctionPayload))).thenReturn(
          expectedTotal2
        )
        when(registrationConnector.getRegistration()(any())) `thenReturn` Future.successful(Some(RegistrationData.registration))

        val result = service.toCore(vatReturn2, correctionPayload).futureValue

        result mustBe expectedResultCoreVatReturn2
      }

      "fails when registration returns empty" in {
        when(vatReturnSalesService.getTotalVatOnSalesAfterCorrection(vatReturn, Some(correctionPayload))).thenReturn(
          expectedTotal
        )
        when(registrationConnector.getRegistration()(any())) `thenReturn` Future.successful(None)

        val result = service.toCore(vatReturn, correctionPayload)

        whenReady(result.failed) { exp => exp.getMessage mustBe "Unable to get registration for 12345**** in period 2021-Q3" }
      }

    }

    "convert from VatReturn and correctionPayload to CoreVatReturn" in {

      val correctionPeriod1 = StandardPeriod(2021, Quarter.Q1)
      val correctionPeriod2 = StandardPeriod(2021, Quarter.Q2)

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
        version = vatReturn.lastUpdated.truncatedTo(ChronoUnit.MILLIS),
        traderId = CoreTraderId(vrn.vrn, "XI"),
        period = CorePeriod(period.year, period.quarter.toString.tail.toInt),
        startDate = period.firstDay,
        endDate = period.lastDay,
        submissionDateTime = now.truncatedTo(ChronoUnit.MILLIS),
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
                Some(country2.code),
                None,
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
                Some(country2.code),
                None,
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
        ),
        changeDate = registration.adminUse.changeDate
      )

      when(registrationConnector.getRegistration()(any())) `thenReturn` Future.successful(Some(RegistrationData.registration))

      service.toCore(vatReturn, correctionPayload).futureValue mustBe expectedResultCoreVatReturn
    }

    "convert from VatReturn and correctionPayload to CoreVatReturn with a Nil return and a correction" in {

      val correctionPeriod1 = StandardPeriod(2021, Quarter.Q1)

      val correctionAmount1 = -(salesDetails1.vatOnSales.amount / 2).setScale(2, RoundingMode.HALF_UP)

      val correctionPayload = CorrectionPayload(vrn, period, List(
        PeriodWithCorrections(
          correctionReturnPeriod = correctionPeriod1,
          correctionsToCountry = List(
            CorrectionToCountry(country1, correctionAmount1)
          )
        )
      ), now, now)

      val expectedTotal = correctionAmount1

      val nilVatReturn = vatReturn.copy(salesFromNi = List.empty, salesFromEu = List.empty)

      when(vatReturnSalesService.getTotalVatOnSalesAfterCorrection(nilVatReturn, Some(correctionPayload))).thenReturn(
        expectedTotal
      )

      val expectedResultCoreVatReturn = CoreVatReturn(
        vatReturnReferenceNumber = returnReference.value,
        version = vatReturn.lastUpdated.truncatedTo(ChronoUnit.MILLIS),
        traderId = CoreTraderId(vrn.vrn, "XI"),
        period = CorePeriod(period.year, period.quarter.toString.tail.toInt),
        startDate = period.firstDay,
        endDate = period.lastDay,
        submissionDateTime = now.truncatedTo(ChronoUnit.MILLIS),
        totalAmountVatDueGBP = expectedTotal,
        msconSupplies = List(
          CoreMsconSupply(
            msconCountryCode = country1.code,
            balanceOfVatDueGBP = correctionAmount1,
            grandTotalMsidGoodsGBP = 0,
            grandTotalMsestGoodsGBP = 0,
            correctionsTotalGBP = correctionAmount1,
            msidSupplies = List.empty,
            msestSupplies = List.empty,
            corrections = List(
              CoreCorrection(
                period = CorePeriod(correctionPeriod1.year, correctionPeriod1.quarter.toString.tail.toInt),
                totalVatAmountCorrectionGBP = correctionAmount1
              )
            )
          )
        ),
        changeDate = registration.adminUse.changeDate
      )

      when(registrationConnector.getRegistration()(any())) `thenReturn` Future.successful(Some(RegistrationData.registration))

      service.toCore(nilVatReturn, correctionPayload).futureValue mustBe expectedResultCoreVatReturn
    }

  }

  "CoreVatReturnService#getEuTraderIdForCountry" - {
    
    "not an online marketplace" - {

      "with fixed establishment" - {
        "must return a Some(CoreEuTraderVatId) and strip country code when registration contains a VatId and fixed establishment" in {

          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = Some(CoreEuTraderVatId("123456789", "BE"))

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("BE", "Belgium"), RegistrationData.registration))

          result mustBe expected
        }

        "must return a Some(CoreEuTraderVatId) and not strip country code when it's not there when registration contains a VatId and fixed establishment" in {

          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = Some(CoreEuTraderVatId("123456789", "ES"))

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("ES", "Spain"), RegistrationData.registration))

          result mustBe expected
        }

        "must return a Some(CoreEuTraderVatId) and not strip country code for France when registration contains a VatId shorter than 13 characters and fixed establishment" in {

          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = Some(CoreEuTraderVatId("FR123456789", "FR"))

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("FR", "France"), RegistrationData.registration.copy(euRegistrations = Seq(
            RegistrationWithFixedEstablishment(
              Country("FR", "France"),
              EuTaxIdentifier(EuTaxIdentifierType.Vat, "FR123456789"),
              TradeDetails("french trading name", InternationalAddress("Line 1", None, "Town", None, None, Country("FR", "France")))
            )))))

          result mustBe expected
        }

        "must return a Some(CoreEuTraderVatId) and strip country code for France when registration contains a VatId of 13 chars and fixed establishment" in {

          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = Some(CoreEuTraderVatId("FR123456789", "FR"))

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("FR", "France"), RegistrationData.registration.copy(euRegistrations = Seq(
            RegistrationWithFixedEstablishment(
              Country("FR", "France"),
              EuTaxIdentifier(EuTaxIdentifierType.Vat, "FRFR123456789"),
              TradeDetails("french trading name", InternationalAddress("Line 1", None, "Town", None, None, Country("FR", "France")))
            )))))

          result mustBe expected
        }

        "must return a Some(CoreEuTraderVatId) and not strip country code for Netherlands when registration contains a VatId shorter than 14 characters and fixed establishment" in {

          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = Some(CoreEuTraderVatId("NL1234567890", "NL"))

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("NL", "Netherlands"), RegistrationData.registration.copy(euRegistrations = Seq(
            RegistrationWithFixedEstablishment(
              Country("NL", "Netherlands"),
              EuTaxIdentifier(EuTaxIdentifierType.Vat, "NL1234567890"),
              TradeDetails("Netherlands trading name", InternationalAddress("Line 1", None, "Town", None, None, Country("NL", "Netherlands")))
            )))))

          result mustBe expected
        }

        "must return a Some(CoreEuTraderVatId) and strip country code for Netherlands when registration contains a VatId of 14 chars and fixed establishment" in {

          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = Some(CoreEuTraderVatId("NL1234567890", "NL"))

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("NL", "Netherlands"), RegistrationData.registration.copy(euRegistrations = Seq(
            RegistrationWithFixedEstablishment(
              Country("NL", "Netherlands"),
              EuTaxIdentifier(EuTaxIdentifierType.Vat, "NLNL1234567890"),
              TradeDetails("Netherlands trading name", InternationalAddress("Line 1", None, "Town", None, None, Country("NL", "Netherlands")))
            )))))

          result mustBe expected
        }

        "must return a Some(CoreEuTraderTaxId) when registration contains a TaxId" in {
          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = Some(CoreEuTraderTaxId("PL123456789", "PL"))

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("PL", "Poland"), RegistrationData.registration))

          result mustBe expected
        }
      }

      "without fixed establishment with send goods trade details" - {
        "must return a Some(CoreEuTraderVatId) and strip country code when registration contains a VatId and fixed establishment" in {

          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = Some(CoreEuTraderVatId("123456789", "ES"))

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("ES", "Spain"), RegistrationData.registration.copy(euRegistrations = Seq(
            RegistrationWithoutFixedEstablishmentWithTradeDetails(
              Country("ES", "Spain"),
              EuTaxIdentifier(EuTaxIdentifierType.Vat, "ES123456789"),
              TradeDetails("spanish trading name", InternationalAddress("Line 1", None, "Town", None, None, Country("ES", "Spain")))
            )))))
          result mustBe expected
        }

        "must return a Some(CoreEuTraderVatId) and not strip country code when it's not there when registration contains a VatId and fixed establishment" in {

          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = Some(CoreEuTraderVatId("123456789", "ES"))

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("ES", "Spain"), RegistrationData.registration.copy(euRegistrations = Seq(
            RegistrationWithoutFixedEstablishmentWithTradeDetails(
              Country("ES", "Spain"),
              EuTaxIdentifier(EuTaxIdentifierType.Vat, "123456789"),
              TradeDetails("spanish trading name", InternationalAddress("Line 1", None, "Town", None, None, Country("ES", "Spain")))
            )))))
          result mustBe expected
        }

        "must return a Some(CoreEuTraderVatId) and not strip country code for France when registration contains a VatId shorter than 13 characters and fixed establishment" in {

          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = Some(CoreEuTraderVatId("FR123456789", "FR"))

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("FR", "France"), RegistrationData.registration.copy(euRegistrations = Seq(
            RegistrationWithoutFixedEstablishmentWithTradeDetails(
              Country("FR", "France"),
              EuTaxIdentifier(EuTaxIdentifierType.Vat, "FR123456789"),
              TradeDetails("french trading name", InternationalAddress("Line 1", None, "Town", None, None, Country("FR", "France")))
            )))))

          result mustBe expected
        }

        "must return a Some(CoreEuTraderVatId) and strip country code for France when registration contains a VatId of 13 chars and fixed establishment" in {

          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = Some(CoreEuTraderVatId("FR123456789", "FR"))

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("FR", "France"), RegistrationData.registration.copy(euRegistrations = Seq(
            RegistrationWithoutFixedEstablishmentWithTradeDetails(
              Country("FR", "France"),
              EuTaxIdentifier(EuTaxIdentifierType.Vat, "FRFR123456789"),
              TradeDetails("french trading name", InternationalAddress("Line 1", None, "Town", None, None, Country("FR", "France")))
            )))))

          result mustBe expected
        }

        "must return a Some(CoreEuTraderVatId) and not strip country code for Netherlands when registration contains a VatId shorter than 14 characters and fixed establishment" in {

          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = Some(CoreEuTraderVatId("NL1234567890", "NL"))

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("NL", "Netherlands"), RegistrationData.registration.copy(euRegistrations = Seq(
            RegistrationWithoutFixedEstablishmentWithTradeDetails(
              Country("NL", "Netherlands"),
              EuTaxIdentifier(EuTaxIdentifierType.Vat, "NL1234567890"),
              TradeDetails("Netherlands trading name", InternationalAddress("Line 1", None, "Town", None, None, Country("NL", "Netherlands")))
            )))))

          result mustBe expected
        }

        "must return a Some(CoreEuTraderVatId) and strip country code for Netherlands when registration contains a VatId of 14 chars and fixed establishment" in {

          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = Some(CoreEuTraderVatId("NL1234567890", "NL"))

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("NL", "Netherlands"), RegistrationData.registration.copy(euRegistrations = Seq(
            RegistrationWithoutFixedEstablishmentWithTradeDetails(
              Country("NL", "Netherlands"),
              EuTaxIdentifier(EuTaxIdentifierType.Vat, "NLNL1234567890"),
              TradeDetails("Netherlands trading name", InternationalAddress("Line 1", None, "Town", None, None, Country("NL", "Netherlands")))
            )))))

          result mustBe expected
        }

        "must return a Some(CoreEuTraderTaxId) when registration contains a TaxId" in {
          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = Some(CoreEuTraderTaxId("PL123456789", "PL"))

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("PL", "Poland"), RegistrationData.registration.copy(euRegistrations = Seq(
            RegistrationWithoutFixedEstablishmentWithTradeDetails(
              Country("PL", "Poland"),
              EuTaxIdentifier(EuTaxIdentifierType.Other, "PL123456789"),
              TradeDetails("polish trading name", InternationalAddress("Line 1", None, "Town", None, None, Country("PL", "Poland")))
            )))))
          result mustBe expected
        }

      }

      "with no fixed establishment and no trade details" - {
        "must return a None when registration contains a VatId" in {

          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = None

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("IT", "Italy"), RegistrationData.registration
            .copy(euRegistrations = Seq(
              RegistrationWithoutFixedEstablishment(
                Country("IT", "Italy"),
                EuTaxIdentifier(EuTaxIdentifierType.Vat, "IT123456789")
              )
            ))
          ))

          result mustBe expected
        }

        "must return a None when registration contains a TaxId" in {
          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = None

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("NL", "Netherlands"), RegistrationData.registration))

          result mustBe expected
        }

        "must return a None when registration contains a VatId as a String" in {
          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = None

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("FR", "France"), RegistrationData.registration))

          result mustBe expected
        }

        "must return None when registration does not contains a VatId or TaxId" in {
          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = None

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("IR", "Ireland"), RegistrationData.registration))

          result mustBe expected
        }
      }
      
    }   
    
    "is an online marketplace" - {
      
      val onlineMarketplaceRegistration = RegistrationData.registration.copy(isOnlineMarketplace = true)

      "with fixed establishment" - {
        
        "must return a None and strip country code when registration contains a VatId and fixed establishment" in {

          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = None

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("BE", "Belgium"), onlineMarketplaceRegistration))

          result mustBe expected
        }

        "must return a None and not strip country code when it's not there when registration contains a VatId and fixed establishment" in {

          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = None

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("ES", "Spain"), onlineMarketplaceRegistration))

          result mustBe expected
        }

        "must return a None and not strip country code for France when registration contains a VatId shorter than 13 characters and fixed establishment" in {

          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = None

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("FR", "France"), onlineMarketplaceRegistration.copy(euRegistrations = Seq(
            RegistrationWithFixedEstablishment(
              Country("FR", "France"),
              EuTaxIdentifier(EuTaxIdentifierType.Vat, "FR123456789"),
              TradeDetails("french trading name", InternationalAddress("Line 1", None, "Town", None, None, Country("FR", "France")))
            )))))

          result mustBe expected
        }

        "must return a None for France when registration contains a VatId of 13 chars and fixed establishment" in {

          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = None

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("FR", "France"), onlineMarketplaceRegistration.copy(euRegistrations = Seq(
            RegistrationWithFixedEstablishment(
              Country("FR", "France"),
              EuTaxIdentifier(EuTaxIdentifierType.Vat, "FRFR123456789"),
              TradeDetails("french trading name", InternationalAddress("Line 1", None, "Town", None, None, Country("FR", "France")))
            )))))

          result mustBe expected
        }

        "must return a None and not strip country code for Netherlands when registration contains a VatId shorter than 14 characters and fixed establishment" in {

          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = None

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("NL", "Netherlands"), onlineMarketplaceRegistration.copy(euRegistrations = Seq(
            RegistrationWithFixedEstablishment(
              Country("NL", "Netherlands"),
              EuTaxIdentifier(EuTaxIdentifierType.Vat, "NL1234567890"),
              TradeDetails("Netherlands trading name", InternationalAddress("Line 1", None, "Town", None, None, Country("NL", "Netherlands")))
            )))))

          result mustBe expected
        }

        "must return a None and strip country code for Netherlands when registration contains a VatId of 14 chars and fixed establishment" in {

          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = None

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("NL", "Netherlands"), onlineMarketplaceRegistration.copy(euRegistrations = Seq(
            RegistrationWithFixedEstablishment(
              Country("NL", "Netherlands"),
              EuTaxIdentifier(EuTaxIdentifierType.Vat, "NLNL1234567890"),
              TradeDetails("Netherlands trading name", InternationalAddress("Line 1", None, "Town", None, None, Country("NL", "Netherlands")))
            )))))

          result mustBe expected
        }

        "must return a None when registration contains a TaxId" in {
          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = None

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("PL", "Poland"), onlineMarketplaceRegistration))

          result mustBe expected
        }
      }

      "without fixed establishment with send goods trade details" - {
        "must return a None and strip country code when registration contains a VatId and fixed establishment" in {

          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = None

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("ES", "Spain"), onlineMarketplaceRegistration.copy(euRegistrations = Seq(
            RegistrationWithoutFixedEstablishmentWithTradeDetails(
              Country("ES", "Spain"),
              EuTaxIdentifier(EuTaxIdentifierType.Vat, "ES123456789"),
              TradeDetails("spanish trading name", InternationalAddress("Line 1", None, "Town", None, None, Country("ES", "Spain")))
            )))))
          result mustBe expected
        }

        "must return a None and not strip country code when it's not there when registration contains a VatId and fixed establishment" in {

          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = None

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("ES", "Spain"), onlineMarketplaceRegistration.copy(euRegistrations = Seq(
            RegistrationWithoutFixedEstablishmentWithTradeDetails(
              Country("ES", "Spain"),
              EuTaxIdentifier(EuTaxIdentifierType.Vat, "123456789"),
              TradeDetails("spanish trading name", InternationalAddress("Line 1", None, "Town", None, None, Country("ES", "Spain")))
            )))))
          result mustBe expected
        }

        "must return a None and not strip country code for France when registration contains a VatId shorter than 13 characters and fixed establishment" in {

          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = None

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("FR", "France"), onlineMarketplaceRegistration.copy(euRegistrations = Seq(
            RegistrationWithoutFixedEstablishmentWithTradeDetails(
              Country("FR", "France"),
              EuTaxIdentifier(EuTaxIdentifierType.Vat, "FR123456789"),
              TradeDetails("french trading name", InternationalAddress("Line 1", None, "Town", None, None, Country("FR", "France")))
            )))))

          result mustBe expected
        }

        "must return a None and strip country code for France when registration contains a VatId of 13 chars and fixed establishment" in {

          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = None

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("FR", "France"), onlineMarketplaceRegistration.copy(euRegistrations = Seq(
            RegistrationWithoutFixedEstablishmentWithTradeDetails(
              Country("FR", "France"),
              EuTaxIdentifier(EuTaxIdentifierType.Vat, "FRFR123456789"),
              TradeDetails("french trading name", InternationalAddress("Line 1", None, "Town", None, None, Country("FR", "France")))
            )))))

          result mustBe expected
        }

        "must return a None and not strip country code for Netherlands when registration contains a VatId shorter than 14 characters and fixed establishment" in {

          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = None

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("NL", "Netherlands"), onlineMarketplaceRegistration.copy(euRegistrations = Seq(
            RegistrationWithoutFixedEstablishmentWithTradeDetails(
              Country("NL", "Netherlands"),
              EuTaxIdentifier(EuTaxIdentifierType.Vat, "NL1234567890"),
              TradeDetails("Netherlands trading name", InternationalAddress("Line 1", None, "Town", None, None, Country("NL", "Netherlands")))
            )))))

          result mustBe expected
        }

        "must return a None and strip country code for Netherlands when registration contains a VatId of 14 chars and fixed establishment" in {

          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = None

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("NL", "Netherlands"), onlineMarketplaceRegistration.copy(euRegistrations = Seq(
            RegistrationWithoutFixedEstablishmentWithTradeDetails(
              Country("NL", "Netherlands"),
              EuTaxIdentifier(EuTaxIdentifierType.Vat, "1234567890"),
              TradeDetails("Netherlands trading name", InternationalAddress("Line 1", None, "Town", None, None, Country("NL", "Netherlands")))
            )))))

          result mustBe expected
        }

        "must return a None when registration contains a TaxId" in {
          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = None

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("PL", "Poland"), onlineMarketplaceRegistration.copy(euRegistrations = Seq(
            RegistrationWithoutFixedEstablishmentWithTradeDetails(
              Country("PL", "Poland"),
              EuTaxIdentifier(EuTaxIdentifierType.Other, "PL123456789"),
              TradeDetails("polish trading name", InternationalAddress("Line 1", None, "Town", None, None, Country("PL", "Poland")))
            )))))
          result mustBe expected
        }

      }

      "with no fixed establishment and no trade details" - {
        "must return a None when registration contains a VatId" in {

          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = None

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("IT", "Italy"), onlineMarketplaceRegistration
            .copy(euRegistrations = Seq(
              RegistrationWithoutFixedEstablishment(
                Country("IT", "Italy"),
                EuTaxIdentifier(EuTaxIdentifierType.Vat, "IT123456789")
              )
            ))
          ))

          result mustBe expected
        }

        "must return a None when registration contains a TaxId" in {
          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = None

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("NL", "Netherlands"), onlineMarketplaceRegistration))

          result mustBe expected
        }

        "must return a None when registration contains a VatId as a String" in {
          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = None

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("FR", "France"), onlineMarketplaceRegistration))

          result mustBe expected
        }

        "must return None when registration does not contains a VatId or TaxId" in {
          val getEuTraderIdForCountry = PrivateMethod[Option[CoreEuTraderId]](Symbol("getEuTraderIdForCountry"))

          val expected = None

          val result = service.invokePrivate(getEuTraderIdForCountry(new Country("IR", "Ireland"), onlineMarketplaceRegistration))

          result mustBe expected
        }
      }
      
    }

  }
}

