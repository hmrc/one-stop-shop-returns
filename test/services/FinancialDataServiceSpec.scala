package services

import base.SpecBase
import config.AppConfig
import connectors.{FinancialDataConnector, VatReturnConnector}
import models.Period.toEtmpPeriodString
import models.Quarter.*
import models.*
import models.corrections.{CorrectionPayload, CorrectionToCountry, PeriodWithCorrections}
import models.des.{DesException, UnexpectedResponseStatus}
import models.etmp.EtmpObligationsFulfilmentStatus.Fulfilled
import models.etmp.{EtmpObligation, EtmpObligationDetails, EtmpObligations, EtmpVatReturn}
import models.financialdata.*
import org.mockito.ArgumentMatchers.eq as eqTo
import org.mockito.ArgumentMatchers.{any, eq as equalTo}
import org.mockito.Mockito
import org.mockito.Mockito.*
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.BeforeAndAfterEach
import uk.gov.hmrc.domain.Vrn

import java.time.{Instant, LocalDate, ZonedDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FinancialDataServiceSpec extends SpecBase
  with BeforeAndAfterEach {

  private val periodService = mock[PeriodService]
  private val financialDataConnector = mock[FinancialDataConnector]
  private val vatReturnService = mock[VatReturnService]
  private val vatReturnSalesService = mock[VatReturnSalesService]
  private val correctionsService = mock[CorrectionService]
  private val vatReturnConnector = mock[VatReturnConnector]
  private val appConfig = mock[AppConfig]

  private val financialDataService =
    new FinancialDataService(financialDataConnector, vatReturnService, vatReturnSalesService, vatReturnConnector, periodService, correctionsService, stubClock, appConfig)

  private val periodYear2021 = PeriodYear(2021)
  private val queryParameters2021 =
    FinancialDataQueryParameters(fromDate = Some(periodYear2021.startOfYear), toDate = Some(periodYear2021.endOfYear))
  val vatReturn: VatReturn = arbitrary[VatReturn].sample.value.copy(vrn, period = period)
  val etmpVatReturn: EtmpVatReturn = arbitrary[EtmpVatReturn].sample.value.copy(periodKey = toEtmpPeriodString(period))
  val etmpObligations: EtmpObligations = EtmpObligations(obligations = Seq(EtmpObligation(Seq(EtmpObligationDetails(Fulfilled, toEtmpPeriodString(period))))))
  val correctionPayload: CorrectionPayload =
    CorrectionPayload(
      vatReturn.vrn,
      StandardPeriod(2021, Q4),
      List(PeriodWithCorrections(
        period,
        List(Arbitrary.arbitrary[CorrectionToCountry].sample.value)
      )),
      Instant.ofEpochSecond(1630670836),
      Instant.ofEpochSecond(1630670836)
    )

  override def beforeEach(): Unit = {
    Mockito.reset(periodService)
    Mockito.reset(financialDataConnector)
    Mockito.reset(vatReturnService)
    Mockito.reset(vatReturnSalesService)
    Mockito.reset(correctionsService)
    Mockito.reset(vatReturnConnector)
  }

  ".getFinancialData(vrn: Vrn, fromDate: LocalDate)" - {

    val items = Seq(
      Item(
        amount = None,
        clearingReason = None,
        paymentReference = None,
        paymentAmount = None,
        paymentMethod = None
      )
    )

    val financialTransactions = Seq(
      FinancialTransaction(
        chargeType = None,
        mainType = None,
        taxPeriodFrom = Some(LocalDate.of(2021, 7, 1)),
        taxPeriodTo = Some(LocalDate.of(2021, 9, 30)),
        originalAmount = None,
        outstandingAmount = None,
        clearedAmount = None,
        items = Some(items)
      )
    )

    "must return Some(FinancialData) for 1 period year" in {
      val commencementDate = LocalDate.of(2021, 9, 1)

      val financialData =
        FinancialData(Some("VRN"), Some("123456789"), Some("ECOM"), ZonedDateTime.now(), Option(financialTransactions))

      when(periodService.getPeriodYears(any())) `thenReturn` Seq(periodYear2021)
      when(financialDataConnector.getFinancialData(any(), equalTo(queryParameters2021))) `thenReturn` Future.successful(Right(Some(financialData)))

      financialDataService.getFinancialData(Vrn("123456789"), commencementDate).futureValue mustBe Some(financialData)
    }

    "must return Some(FinancialData) for 2 period years" in {
      val commencementDate = LocalDate.of(2021, 9, 1)
      val periodYear2 = PeriodYear(2022)
      val queryParameters2 =
        FinancialDataQueryParameters(fromDate = Some(periodYear2.startOfYear), toDate = Some(periodYear2.endOfYear))

      val financialTransactions2 = Seq(
        FinancialTransaction(
          chargeType = None,
          mainType = None,
          taxPeriodFrom = Some(LocalDate.of(2022, 1, 1)),
          taxPeriodTo = Some(LocalDate.of(2022, 3, 31)),
          originalAmount = None,
          outstandingAmount = None,
          clearedAmount = None,
          items = Some(items)
        )
      )
      val financialData =
        FinancialData(Some("VRN"), Some("123456789"), Some("ECOM"), ZonedDateTime.now(), Option(financialTransactions))

      val financialData2 =
        FinancialData(Some("VRN"), Some("123456789"), Some("ECOM"), ZonedDateTime.now(), Option(financialTransactions2))

      when(periodService.getPeriodYears(any())).thenReturn(Seq(periodYear2021, periodYear2))
      when(financialDataConnector.getFinancialData(any(), any()))
        .thenReturn(Future.successful(Right(Some(financialData))))
        .thenReturn(Future.successful(Right(Some(financialData2))))

      val response = financialDataService.getFinancialData(Vrn("123456789"), commencementDate).futureValue

      response mustBe Some(
        financialData.copy(
          financialTransactions = Some(financialTransactions ++ financialTransactions2)
        )
      )
      verify(financialDataConnector, times(1))
        .getFinancialData(any(), eqTo(queryParameters2021))
      verify(financialDataConnector, times(1))
        .getFinancialData(any(), eqTo(queryParameters2))
    }

    "must return None when no financialData" in {
      val commencementDate = LocalDate.of(2021, 9, 1)

      when(periodService.getPeriodYears(any())).thenReturn(Seq(periodYear2021))
      when(financialDataConnector.getFinancialData(any(), equalTo(queryParameters2021)))
        .thenReturn(Future.successful(Right(None)))

      financialDataService.getFinancialData(Vrn("123456789"), commencementDate).futureValue mustBe None
    }
  }

  ".getCharge" - {

    "return a charge" - {

      "when there has been no payments" in {
        val period = StandardPeriod(2021, Q3)

        val financialTransactions = Seq(
          FinancialTransaction(
            chargeType = Some("G Ret AT EU-OMS"),
            mainType = None,
            taxPeriodFrom = Some(period.firstDay),
            taxPeriodTo = Some(period.lastDay),
            originalAmount = Some(BigDecimal(1000)),
            outstandingAmount = Some(BigDecimal(1000)),
            clearedAmount = Some(BigDecimal(0)),
            items = Some(Seq.empty)
          )
        )
        val queryParameters = FinancialDataQueryParameters(fromDate = Some(period.firstDay), toDate = Some(period.lastDay))

        when(periodService.getPeriodYears(any())) `thenReturn` Seq(periodYear2021)
        when(financialDataConnector.getFinancialData(any(), equalTo(queryParameters))) `thenReturn`
          Future.successful(Right(Some(FinancialData(
            Some("VRN"), Some("123456789"), Some("ECOM"), ZonedDateTime.now(), Option(financialTransactions)))))

        val response = financialDataService.getCharge(Vrn("123456789"), period).futureValue

        response.isDefined mustBe true
        val charge = response.get
        charge.period mustBe period
        charge.originalAmount mustBe BigDecimal(1000)
        charge.outstandingAmount mustBe BigDecimal(1000)
        charge.clearedAmount mustBe BigDecimal(0)
      }

      "when there has been a payment and a single transaction" in {
        val period = StandardPeriod(2021, Q3)

        val items = Seq(
          Item(
            amount = Some(BigDecimal(500)),
            clearingReason = Some("01"),
            paymentReference = Some("a"),
            paymentAmount = Some(BigDecimal(500)),
            paymentMethod = Some("A")
          )
        )

        val financialTransactions = Seq(
          FinancialTransaction(
            chargeType = Some("G Ret AT EU-OMS"),
            mainType = None,
            taxPeriodFrom = Some(period.firstDay),
            taxPeriodTo = Some(period.lastDay),
            originalAmount = Some(BigDecimal(1000)),
            outstandingAmount = Some(BigDecimal(500)),
            clearedAmount = Some(BigDecimal(500)),
            items = Some(items)
          )
        )

        val queryParameters = FinancialDataQueryParameters(fromDate = Some(period.firstDay), toDate = Some(period.lastDay))

        when(periodService.getPeriodYears(any())) `thenReturn` Seq(periodYear2021)
        when(financialDataConnector.getFinancialData(any(), equalTo(queryParameters))) `thenReturn`
          Future.successful(Right(Some(FinancialData(
            Some("VRN"), Some("123456789"), Some("ECOM"), ZonedDateTime.now(), Option(financialTransactions)))))

        val response = financialDataService.getCharge(Vrn("123456789"), period).futureValue

        response.isDefined mustBe true
        val charge = response.get
        charge.period mustBe period
        charge.originalAmount mustBe BigDecimal(1000)
        charge.outstandingAmount mustBe BigDecimal(500)
        charge.clearedAmount mustBe BigDecimal(500)
      }

      "when there has been two transactions and two payments" in {
        val period = StandardPeriod(2021, Q3)
        val items = Seq(
          Item(
            amount = Some(BigDecimal(500)),
            clearingReason = Some("01"),
            paymentReference = Some("a"),
            paymentAmount = Some(BigDecimal(500)),
            paymentMethod = Some("A")
          )
        )

        val items2 = Seq(
          Item(
            amount = Some(BigDecimal(1000)),
            clearingReason = Some("01"),
            paymentReference = Some("a"),
            paymentAmount = Some(BigDecimal(1000)),
            paymentMethod = Some("A")
          )
        )

        val financialTransactions = Seq(
          FinancialTransaction(
            chargeType = Some("G Ret AT EU-OMS"),
            mainType = None,
            taxPeriodFrom = Some(period.firstDay),
            taxPeriodTo = Some(period.lastDay),
            originalAmount = Some(BigDecimal(1000)),
            outstandingAmount = Some(BigDecimal(500)),
            clearedAmount = Some(BigDecimal(500)),
            items = Some(items)
          ),
          FinancialTransaction(
            chargeType = Some("G Ret FR EU-OMS"),
            mainType = None,
            taxPeriodFrom = Some(period.firstDay),
            taxPeriodTo = Some(period.lastDay),
            originalAmount = Some(BigDecimal(1500)),
            outstandingAmount = Some(BigDecimal(500)),
            clearedAmount = Some(BigDecimal(1000)),
            items = Some(items2)
          )
        )

        val queryParameters = FinancialDataQueryParameters(fromDate = Some(period.firstDay), toDate = Some(period.lastDay))

        when(periodService.getPeriodYears(any())) `thenReturn` Seq(periodYear2021)
        when(financialDataConnector.getFinancialData(any(), equalTo(queryParameters))) `thenReturn`
          Future.successful(Right(Some(FinancialData(
            Some("VRN"), Some("123456789"), Some("ECOM"), ZonedDateTime.now(), Option(financialTransactions)))))

        val response = financialDataService.getCharge(Vrn("123456789"), period).futureValue

        response.isDefined mustBe true
        val charge = response.get
        charge.period mustBe period
        charge.originalAmount mustBe BigDecimal(2500)
        charge.outstandingAmount mustBe BigDecimal(1000)
        charge.clearedAmount mustBe BigDecimal(1500)
      }
    }

    "not return a charge" - {

      "when there is no financial data" in {
        val period = StandardPeriod(2021, Q3)
        val queryParameters = FinancialDataQueryParameters(fromDate = Some(period.firstDay), toDate = Some(period.lastDay))

        when(periodService.getPeriodYears(any())) `thenReturn` Seq(periodYear2021)
        when(financialDataConnector.getFinancialData(any(), equalTo(queryParameters)))
          .thenReturn(Future.successful(Right(None)))

        financialDataService.getCharge(Vrn("123456789"), period).futureValue mustBe None
      }
    }
  }

  ".getOutstandingAmounts" - {

    "return a period with outstanding amounts" - {

      "when there has been no payments for 1 period" in {

        val period = StandardPeriod(2021, Q3)
        val vatReturn = arbitrary[VatReturn].sample.value.copy(period = period)

        val financialTransactions = Seq(
          FinancialTransaction(
            chargeType = Some("G Ret AT EU-OMS"),
            mainType = None,
            taxPeriodFrom = Some(period.firstDay),
            taxPeriodTo = Some(period.lastDay),
            originalAmount = Some(BigDecimal(1000)),
            outstandingAmount = Some(BigDecimal(1000)),
            clearedAmount = Some(BigDecimal(0)),
            items = Some(Seq.empty)
          )
        )

        val queryParameters =
          FinancialDataQueryParameters(
            fromDate = None, toDate = None, onlyOpenItems = Some(true)
          )

        when(vatReturnService.get(any())) `thenReturn` Future.successful(Seq(vatReturn))
        when(financialDataConnector.getFinancialData(any(), equalTo(queryParameters))) `thenReturn`
          Future.successful(Right(Some(FinancialData(
            Some("VRN"), Some("123456789"), Some("ECOM"), ZonedDateTime.now(), Option(financialTransactions))
          )))

        financialDataService.getOutstandingAmounts(Vrn("123456789")).futureValue
          .mustBe(Seq(PeriodWithOutstandingAmount(period, BigDecimal(1000))))

        verify(financialDataConnector, times(1)).getFinancialData(any(), any())
      }

      "when there has been no payments for 2 periods with different years" in {

        val period = StandardPeriod(2021, Q4)
        val period2 = StandardPeriod(2022, Q1)
        val vatReturn = arbitrary[VatReturn].sample.value.copy(period = period)

        val financialTransactions = Seq(
          FinancialTransaction(
            chargeType = Some("G Ret AT EU-OMS"),
            mainType = None,
            taxPeriodFrom = Some(period.firstDay),
            taxPeriodTo = Some(period.lastDay),
            originalAmount = Some(BigDecimal(1000)),
            outstandingAmount = Some(BigDecimal(1000)),
            clearedAmount = Some(BigDecimal(0)),
            items = Some(Seq.empty)
          ),
          FinancialTransaction(
            chargeType = Some("G Ret DE EU-OMS"),
            mainType = None,
            taxPeriodFrom = Some(period2.firstDay),
            taxPeriodTo = Some(period2.lastDay),
            originalAmount = Some(BigDecimal(2000)),
            outstandingAmount = Some(BigDecimal(2000)),
            clearedAmount = Some(BigDecimal(0)),
            items = Some(Seq.empty)
          )
        )

        val queryParameters =
          FinancialDataQueryParameters(
            fromDate = None, toDate = None, onlyOpenItems = Some(true)
          )

        when(vatReturnService.get(any())) `thenReturn` Future.successful(Seq(vatReturn))
        when(financialDataConnector.getFinancialData(any(), equalTo(queryParameters))) `thenReturn`
          Future.successful(Right(Some(FinancialData(
            Some("VRN"), Some("123456789"), Some("ECOM"), ZonedDateTime.now(), Option(financialTransactions)))))

        financialDataService.getOutstandingAmounts(Vrn("123456789")).futureValue
          .mustBe(Seq(PeriodWithOutstandingAmount(period2, BigDecimal(2000)), PeriodWithOutstandingAmount(period, BigDecimal(1000))))

        verify(financialDataConnector, times(1)).getFinancialData(any(), any())
      }
    }

    "return empty" - {

      "when there are no transactions found" in {
        val period = StandardPeriod(2021, Q3)
        val vatReturn = arbitrary[VatReturn].sample.value.copy(period = period)

        val financialTransactions = Seq.empty
        val queryParameters =
          FinancialDataQueryParameters(
            fromDate = None, toDate = None, onlyOpenItems = Some(true)
          )

        when(vatReturnService.get(any())) `thenReturn` Future.successful(Seq(vatReturn))
        when(financialDataConnector.getFinancialData(any(), equalTo(queryParameters))) `thenReturn`
          Future.successful(Right(Some(FinancialData(
            Some("VRN"), Some("123456789"), Some("ECOM"), ZonedDateTime.now(), Option(financialTransactions)))))


        financialDataService.getOutstandingAmounts(Vrn("123456789")).futureValue mustBe Seq.empty
      }
    }

    "return DES exception when no tax period from exists" in {
      val period = StandardPeriod(2021, Q3)
      val vatReturn = arbitrary[VatReturn].sample.value.copy(period = period)

      val financialTransactions = Seq(
        FinancialTransaction(
          chargeType = Some("G Ret AT EU-OMS"),
          mainType = None,
          taxPeriodFrom = None,
          taxPeriodTo = Some(period.lastDay),
          originalAmount = Some(BigDecimal(1000)),
          outstandingAmount = Some(BigDecimal(1000)),
          clearedAmount = Some(BigDecimal(0)),
          items = Some(Seq.empty)
        )
      )

      val queryParameters =
        FinancialDataQueryParameters(
          fromDate = None, toDate = None, onlyOpenItems = Some(true)
        )

      when(vatReturnService.get(any())) `thenReturn` Future.successful(Seq(vatReturn))
      when(financialDataConnector.getFinancialData(any(), equalTo(queryParameters))) `thenReturn`
        Future.successful(Right(Some(FinancialData(
          Some("VRN"), Some("123456789"), Some("ECOM"), ZonedDateTime.now(), Option(financialTransactions)))))
      whenReady(financialDataService.getOutstandingAmounts(Vrn("123456789")).failed) { exp =>
        exp mustBe DesException("An error occurred while getting financial Data - periodStart was None")
      }
    }
  }

  ".getVatReturnWithFinancialData" - {

    "with strategic api toggled off" - {

      "must return one VatReturnWithFinancialData when there is one vatReturn and one charge" in {
        val commencementDate = LocalDate.now()

        val financialTransactions = Seq(
          FinancialTransaction(
            chargeType = Some("G Ret AT EU-OMS"),
            mainType = None,
            taxPeriodFrom = Some(period.firstDay),
            taxPeriodTo = Some(period.lastDay),
            originalAmount = Some(BigDecimal(1000)),
            outstandingAmount = Some(BigDecimal(1000)),
            clearedAmount = Some(BigDecimal(0)),
            items = Some(Seq.empty)
          )
        )

        val financialData =
          FinancialData(
            Some("VRN"),
            Some("123456789"),
            Some("ECOM"),
            ZonedDateTime.now(),
            Option(financialTransactions)
          )

        when(appConfig.strategicReturnApiEnabled) `thenReturn` false
        when(periodService.getPeriodYears(any())) `thenReturn` Seq(periodYear2021)
        when(financialDataConnector.getFinancialData(any(), equalTo(queryParameters2021))) `thenReturn`
          Future.successful(Right(Some(financialData)))
        when(vatReturnService.get(any())) `thenReturn` Future.successful(Seq(vatReturn))

        val response = financialDataService.getVatReturnWithFinancialData(Vrn("123456789"), commencementDate).futureValue
        val expectedResponse =
          Seq(PeriodWithFinancialData(
            vatReturn.period,
            Some(Charge(period, BigDecimal(1000), BigDecimal(1000), BigDecimal(0))),
            1000,
            true
          ))

        response must contain theSameElementsAs expectedResponse
        verify(financialDataConnector, times(1)).getFinancialData(any(), eqTo(queryParameters2021))
        verify(periodService, times(1)).getPeriodYears(eqTo(commencementDate))
        verify(vatReturnService, times(1)).get(any())
        verifyNoMoreInteractions(vatReturnService)
        verifyNoInteractions(correctionsService)
      }

      "must return one VatReturnWithFinancialData with no charge when there is one vatReturn and no charge" in {
        val commencementDate = LocalDate.now()

        when(appConfig.strategicReturnApiEnabled) `thenReturn` false
        when(periodService.getPeriodYears(any())) `thenReturn` Seq(periodYear2021)
        when(financialDataConnector.getFinancialData(any(), equalTo(queryParameters2021))) `thenReturn`
          Future.successful(Right(None))
        when(vatReturnService.get(any())) `thenReturn` Future.successful(Seq(vatReturn))
        when(vatReturnService.get(any(), any())) `thenReturn` Future.successful(Some(vatReturn))
        when(correctionsService.get(any(), any())) `thenReturn` Future.successful(None)
        when(vatReturnSalesService.getTotalVatOnSalesAfterCorrection(any(), any())) `thenReturn` BigDecimal(0)

        val response = financialDataService.getVatReturnWithFinancialData(Vrn("123456789"), commencementDate).futureValue
        val expectedResponse =
          Seq(PeriodWithFinancialData(vatReturn.period, None, 0, false))

        response mustBe expectedResponse
        verify(financialDataConnector, times(1)).getFinancialData(any(), eqTo(queryParameters2021))
        verify(periodService, times(1)).getPeriodYears(eqTo(commencementDate))
        verify(vatReturnService, times(1)).get(any())
        verify(correctionsService, times(1)).get(eqTo(Vrn("123456789")), eqTo(period))
        verify(vatReturnSalesService, times(1)).getTotalVatOnSalesAfterCorrection(eqTo(vatReturn), eqTo(None))
      }

      "must return one VatReturnWithFinancialData when there is one vatReturn and financialDataConnector call fails" in {
        val commencementDate = LocalDate.now()

        when(appConfig.strategicReturnApiEnabled) `thenReturn` false
        when(periodService.getPeriodYears(any())) `thenReturn` Seq(periodYear2021)
        when(financialDataConnector.getFinancialData(any(), equalTo(queryParameters2021))) `thenReturn`
          Future.successful(Left(UnexpectedResponseStatus(400, "Error")))
        when(vatReturnService.get(any())) `thenReturn` Future.successful(Seq(vatReturn))
        when(vatReturnService.get(any(), any())) `thenReturn` Future.successful(Some(vatReturn))
        when(correctionsService.get(any(), any())) `thenReturn` Future.successful(None)
        when(vatReturnSalesService.getTotalVatOnSalesAfterCorrection(any(), any())) `thenReturn` BigDecimal(0)
        val response = financialDataService.getVatReturnWithFinancialData(Vrn("123456789"), commencementDate).futureValue
        val expectedResponse = Seq(PeriodWithFinancialData(vatReturn.period, None, 0, false))

        response must contain theSameElementsAs expectedResponse
        verify(financialDataConnector, times(1)).getFinancialData(any(), eqTo(queryParameters2021))
        verify(periodService, times(1)).getPeriodYears(eqTo(commencementDate))
        verify(vatReturnService, times(1)).get(any())
        verify(correctionsService, times(1)).get(eqTo(Vrn("123456789")), eqTo(period))
        verify(vatReturnSalesService, times(1)).getTotalVatOnSalesAfterCorrection(eqTo(vatReturn), eqTo(None))

      }

      "must return Seq.empty when there are no vatReturns" in {
        val commencementDate = LocalDate.now()

        when(periodService.getPeriodYears(any())) `thenReturn` Seq(periodYear2021)
        when(financialDataConnector.getFinancialData(any(), equalTo(queryParameters2021))) `thenReturn`
          Future.successful(Right(None))
        when(vatReturnService.get(any())) `thenReturn` Future.successful(Seq.empty)

        val response = financialDataService.getVatReturnWithFinancialData(Vrn("123456789"), commencementDate).futureValue

        response mustBe Seq.empty
        verify(financialDataConnector, times(1)).getFinancialData(any(), eqTo(queryParameters2021))
        verify(periodService, times(1)).getPeriodYears(eqTo(commencementDate))
        verify(vatReturnService, times(1)).get(any())
      }

      "must return something when there are vat returns but 404 financial data" in {
        val commencementDate = LocalDate.of(2021, 7, 1)

        when(appConfig.strategicReturnApiEnabled) `thenReturn` false
        when(periodService.getPeriodYears(any())) `thenReturn` Seq(periodYear2021)
        when(financialDataConnector.getFinancialData(any(), equalTo(queryParameters2021))) `thenReturn`
          Future.successful(Right(None))
        when(vatReturnService.get(any())) `thenReturn` Future.successful(Seq(vatReturn))
        when(vatReturnService.get(any(), any())) `thenReturn` Future.successful(Some(vatReturn))
        when(correctionsService.get(any(), any())) `thenReturn` Future.successful(None)
        when(vatReturnSalesService.getTotalVatOnSalesAfterCorrection(any(), any())) `thenReturn` BigDecimal(1000)
        val response = financialDataService.getVatReturnWithFinancialData(Vrn("123456789"), commencementDate).futureValue

        val expectedResponse =
          Seq(
            PeriodWithFinancialData(
              vatReturn.period, None, 1000, true
            ),
          )

        response mustBe expectedResponse
        verify(financialDataConnector, times(1)).getFinancialData(any(), eqTo(queryParameters2021))
        verify(periodService, times(1)).getPeriodYears(eqTo(commencementDate))
        verify(vatReturnService, times(1)).get(any())
      }

      "must return multiple VatReturnWithFinancialDatas when there is multiple vatReturns in the same period year and charges" in {
        val commencementDate = LocalDate.now()
        val period = StandardPeriod(2021, Q3)
        val period2 = StandardPeriod(2021, Q4)

        val financialTransaction =
          FinancialTransaction(
            chargeType = Some("G Ret AT EU-OMS"),
            mainType = None,
            taxPeriodFrom = Some(period.firstDay),
            taxPeriodTo = Some(period.lastDay),
            originalAmount = Some(BigDecimal(1000)),
            outstandingAmount = Some(BigDecimal(1000)),
            clearedAmount = Some(BigDecimal(0)),
            items = Some(Seq.empty)
          )

        val financialTransactions = Seq(
          financialTransaction,
          financialTransaction.copy(
            taxPeriodTo = Some(period2.firstDay),
            taxPeriodFrom = Some(period2.firstDay)
          )
        )

        val vatReturn = arbitrary[VatReturn].sample.value.copy(vrn, period = period)

        when(appConfig.strategicReturnApiEnabled) `thenReturn` false
        when(periodService.getPeriodYears(any())) `thenReturn` Seq(periodYear2021)
        when(financialDataConnector.getFinancialData(any(), equalTo(queryParameters2021))) `thenReturn`
          Future.successful(
            Right(Some(FinancialData(
              Some("VRN"),
              Some("123456789"),
              Some("ECOM"),
              ZonedDateTime.now(),
              Option(financialTransactions)
            )))
          )
        when(vatReturnService.get(any())) `thenReturn`
          Future.successful(Seq(vatReturn, vatReturn.copy(period = period2)))

        val response = financialDataService.getVatReturnWithFinancialData(Vrn("123456789"), commencementDate).futureValue
        val expectedResponse =
          Seq(
            PeriodWithFinancialData(
              vatReturn.period,
              Some(Charge(period, BigDecimal(1000), BigDecimal(1000), BigDecimal(0))),
              1000,
              true
            ),
            PeriodWithFinancialData(
              period2,
              Some(Charge(period2, BigDecimal(1000), BigDecimal(1000), BigDecimal(0))),
              1000,
              true
            )
          )

        response must contain theSameElementsAs expectedResponse
        verify(financialDataConnector, times(1)).getFinancialData(any(), eqTo(queryParameters2021))
        verify(periodService, times(1)).getPeriodYears(eqTo(commencementDate))
        verify(vatReturnService, times(1)).get(any())
        verifyNoInteractions(correctionsService)
      }

      "must return multiple VatReturnWithFinancialDatas when there is multiple vatReturns in the different period years and charges" in {
        val commencementDate = LocalDate.now()
        val period = StandardPeriod(2021, Q4)
        val period2 = StandardPeriod(2022, Q1)

        val financialTransaction =
          FinancialTransaction(
            chargeType = Some("G Ret AT EU-OMS"),
            mainType = None,
            taxPeriodFrom = Some(period.firstDay),
            taxPeriodTo = Some(period.lastDay),
            originalAmount = Some(BigDecimal(1000)),
            outstandingAmount = Some(BigDecimal(1000)),
            clearedAmount = Some(BigDecimal(0)),
            items = Some(Seq.empty)
          )

        val financialTransaction2 =
          financialTransaction.copy(
            taxPeriodFrom = Some(period2.firstDay),
            taxPeriodTo = Some(period2.lastDay)
          )

        val financialTransactions = Seq(financialTransaction)
        val financialTransactions2 = Seq(financialTransaction2)

        val vatReturn = arbitrary[VatReturn].sample.value.copy(vrn, period = period)
        val periodYear2 = PeriodYear(2022)
        val queryParameters2 =
          FinancialDataQueryParameters(fromDate = Some(periodYear2.startOfYear), toDate = Some(periodYear2.endOfYear))

        when(appConfig.strategicReturnApiEnabled) `thenReturn` false
        when(periodService.getPeriodYears(any())) `thenReturn` Seq(periodYear2021, periodYear2)
        when(financialDataConnector.getFinancialData(any(), any()))
          .thenReturn(
            Future.successful(
              Right(Some(FinancialData(
                Some("VRN"),
                Some("123456789"),
                Some("ECOM"),
                ZonedDateTime.now(),
                Option(financialTransactions)
              )))
            )
          ).thenReturn(
            Future.successful(
              Right(Some(FinancialData(
                Some("VRN"),
                Some("123456789"),
                Some("ECOM"),
                ZonedDateTime.now(),
                Option(financialTransactions2)
              )))
            )
          )
        when(vatReturnService.get(any())) `thenReturn`
          Future.successful(Seq(vatReturn, vatReturn.copy(period = period2)))

        val response = financialDataService.getVatReturnWithFinancialData(Vrn("123456789"), commencementDate).futureValue
        val expectedResponse =
          Seq(
            PeriodWithFinancialData(
              vatReturn.period,
              Some(Charge(period, BigDecimal(1000), BigDecimal(1000), BigDecimal(0))),
              1000,
              true
            ),
            PeriodWithFinancialData(
              period2,
              Some(Charge(period2, BigDecimal(1000), BigDecimal(1000), BigDecimal(0))),
              1000,
              true
            )
          )

        response must contain theSameElementsAs expectedResponse
        verify(financialDataConnector, times(1)).getFinancialData(any(), eqTo(queryParameters2021))
        verify(financialDataConnector, times(1)).getFinancialData(any(), eqTo(queryParameters2))
        verify(periodService, times(1)).getPeriodYears(eqTo(commencementDate))
        verify(vatReturnService, times(1)).get(any())
        verifyNoInteractions(correctionsService)
      }

      "must return one VatReturnWithFinancialData with no charge when there is one vatReturn and no charge with correction" in {
        val commencementDate = LocalDate.now()

        when(appConfig.strategicReturnApiEnabled) `thenReturn` false
        when(financialDataConnector.getFinancialData(any(), eqTo(queryParameters2021))) `thenReturn` Future.successful(Right(None))
        when(vatReturnService.get(any())) `thenReturn` Future.successful(Seq(vatReturn))
        when(vatReturnService.get(any(), any())) `thenReturn` Future.successful(Some(vatReturn))
        when(periodService.getPeriodYears(any())) `thenReturn` Seq(periodYear2021)
        when(correctionsService.get(any(), any())) `thenReturn` Future.successful(Some(correctionPayload))
        when(vatReturnSalesService.getTotalVatOnSalesAfterCorrection(any(), any())) `thenReturn` BigDecimal(100)
        val expectedResponse =
          Seq(PeriodWithFinancialData(vatReturn.period, None, 100, true))

        val response = financialDataService.getVatReturnWithFinancialData(Vrn("123456789"), commencementDate).futureValue

        response mustBe expectedResponse
        verify(financialDataConnector, times(1)).getFinancialData(any(), eqTo(queryParameters2021))
        verify(vatReturnService, times(1)).get(eqTo(Vrn("123456789")))
        verify(periodService, times(1)).getPeriodYears(eqTo(commencementDate))
        verify(correctionsService, times(1)).get(eqTo(Vrn("123456789")), eqTo(period))
        verify(vatReturnSalesService, times(1)).getTotalVatOnSalesAfterCorrection(eqTo(vatReturn), eqTo(Some(correctionPayload)))

      }
    }

    "with strategic api toggled on" - {

      "must return one VatReturnWithFinancialData when there is one vatReturn and one charge" in {
        val commencementDate = LocalDate.now()

        val financialTransactions = Seq(
          FinancialTransaction(
            chargeType = Some("G Ret AT EU-OMS"),
            mainType = None,
            taxPeriodFrom = Some(period.firstDay),
            taxPeriodTo = Some(period.lastDay),
            originalAmount = Some(BigDecimal(1000)),
            outstandingAmount = Some(BigDecimal(1000)),
            clearedAmount = Some(BigDecimal(0)),
            items = Some(Seq.empty)
          )
        )

        val financialData =
          FinancialData(
            Some("VRN"),
            Some("123456789"),
            Some("ECOM"),
            ZonedDateTime.now(),
            Option(financialTransactions)
          )

        when(appConfig.strategicReturnApiEnabled) `thenReturn` true
        when(periodService.getPeriodYears(any())) `thenReturn` Seq(periodYear2021)
        when(financialDataConnector.getFinancialData(any(), equalTo(queryParameters2021))) `thenReturn`
          Future.successful(Right(Some(financialData)))
        when(vatReturnConnector.getObligations(any(), any())) `thenReturn` Future.successful(Right(etmpObligations))

        val response = financialDataService.getVatReturnWithFinancialData(Vrn("123456789"), commencementDate).futureValue
        val expectedResponse =
          Seq(PeriodWithFinancialData(
            vatReturn.period,
            Some(Charge(period, BigDecimal(1000), BigDecimal(1000), BigDecimal(0))),
            1000,
            true
          ))

        response must contain theSameElementsAs expectedResponse
        verify(financialDataConnector, times(1)).getFinancialData(any(), eqTo(queryParameters2021))
        verify(periodService, times(1)).getPeriodYears(eqTo(commencementDate))
        verify(vatReturnConnector, times(1)).getObligations(any(), any())
        verifyNoInteractions(vatReturnService)
        verifyNoInteractions(correctionsService)
      }

      "must return one VatReturnWithFinancialData with no charge when there is one vatReturn and no charge" in {
        val commencementDate = LocalDate.now()

        when(appConfig.strategicReturnApiEnabled) `thenReturn` true
        when(periodService.getPeriodYears(any())) `thenReturn` Seq(periodYear2021)
        when(financialDataConnector.getFinancialData(any(), equalTo(queryParameters2021))) `thenReturn`
          Future.successful(Right(None))
        when(vatReturnConnector.getObligations(any(), any())) `thenReturn` Future.successful(Right(etmpObligations))
        when(vatReturnConnector.get(any(), any())) `thenReturn` Future.successful(Right(etmpVatReturn))

        val response = financialDataService.getVatReturnWithFinancialData(Vrn("123456789"), commencementDate).futureValue

        val expectedResponse =
          Seq(PeriodWithFinancialData(vatReturn.period, None, etmpVatReturn.totalVATAmountDueForAllMSGBP, true))

        response mustBe expectedResponse
        verify(financialDataConnector, times(1)).getFinancialData(any(), eqTo(queryParameters2021))
        verify(periodService, times(1)).getPeriodYears(eqTo(commencementDate))
        verify(vatReturnConnector, times(1)).getObligations(any(), any())
        verify(vatReturnConnector, times(1)).get(any(), any())
        verifyNoInteractions(vatReturnService)
      }

      "must return one VatReturnWithFinancialData when there is one vatReturn and financialDataConnector call fails" in {
        val commencementDate = LocalDate.now()

        when(appConfig.strategicReturnApiEnabled) `thenReturn` true
        when(periodService.getPeriodYears(any())) `thenReturn` Seq(periodYear2021)
        when(financialDataConnector.getFinancialData(any(), equalTo(queryParameters2021))) `thenReturn`
          Future.successful(Left(UnexpectedResponseStatus(400, "Error")))
        when(vatReturnConnector.getObligations(any(), any())) `thenReturn` Future.successful(Right(etmpObligations))
        when(vatReturnConnector.get(any(), any())) `thenReturn` Future.successful(Right(etmpVatReturn))
        val response = financialDataService.getVatReturnWithFinancialData(Vrn("123456789"), commencementDate).futureValue
        val expectedResponse = Seq(PeriodWithFinancialData(vatReturn.period, None, etmpVatReturn.totalVATAmountDueForAllMSGBP, true))

        response must contain theSameElementsAs expectedResponse
        verify(financialDataConnector, times(1)).getFinancialData(any(), eqTo(queryParameters2021))
        verify(periodService, times(1)).getPeriodYears(eqTo(commencementDate))
        verify(vatReturnConnector, times(1)).getObligations(any(), any())
        verify(vatReturnConnector, times(1)).get(any(), any())
        verifyNoInteractions(vatReturnService)
        verifyNoInteractions(correctionsService)
      }

      "must return Seq.empty when there are no vatReturns" in {
        val commencementDate = LocalDate.now()
        val emptyObligations = EtmpObligations(Seq.empty)

        when(appConfig.strategicReturnApiEnabled) `thenReturn` true
        when(periodService.getPeriodYears(any())) `thenReturn` Seq(periodYear2021)
        when(financialDataConnector.getFinancialData(any(), equalTo(queryParameters2021))) `thenReturn`
          Future.successful(Right(None))
        when(vatReturnConnector.getObligations(any(), any())) `thenReturn` Future.successful(Right(emptyObligations))

        val response = financialDataService.getVatReturnWithFinancialData(Vrn("123456789"), commencementDate).futureValue

        response mustBe Seq.empty
        verify(financialDataConnector, times(1)).getFinancialData(any(), eqTo(queryParameters2021))
        verify(periodService, times(1)).getPeriodYears(eqTo(commencementDate))
        verify(vatReturnConnector, times(1)).getObligations(any(), any())
      }

      "must return something when there are vat returns but 404 financial data" in {
        val commencementDate = LocalDate.of(2021, 7, 1)

        when(appConfig.strategicReturnApiEnabled) `thenReturn` true
        when(periodService.getPeriodYears(any())) `thenReturn` Seq(periodYear2021)
        when(financialDataConnector.getFinancialData(any(), equalTo(queryParameters2021))) `thenReturn`
          Future.successful(Right(None))
        when(vatReturnConnector.getObligations(any(), any())) `thenReturn` Future.successful(Right(etmpObligations))
        when(vatReturnConnector.get(any(), any())) `thenReturn` Future.successful(Right(etmpVatReturn))
        val response = financialDataService.getVatReturnWithFinancialData(Vrn("123456789"), commencementDate).futureValue

        val expectedResponse =
          Seq(
            PeriodWithFinancialData(
              vatReturn.period, None, etmpVatReturn.totalVATAmountDueForAllMSGBP, true
            ),
          )

        response mustBe expectedResponse
        verify(financialDataConnector, times(1)).getFinancialData(any(), eqTo(queryParameters2021))
        verify(periodService, times(1)).getPeriodYears(eqTo(commencementDate))
        verify(vatReturnConnector, times(1)).getObligations(any(), any())
      }

      "must return multiple VatReturnWithFinancialDatas when there is multiple vatReturns in the same period year and charges" in {
        val commencementDate = LocalDate.now()
        val period = StandardPeriod(2021, Q3)
        val period2 = StandardPeriod(2021, Q4)

        val financialTransaction =
          FinancialTransaction(
            chargeType = Some("G Ret AT EU-OMS"),
            mainType = None,
            taxPeriodFrom = Some(period.firstDay),
            taxPeriodTo = Some(period.lastDay),
            originalAmount = Some(BigDecimal(1000)),
            outstandingAmount = Some(BigDecimal(1000)),
            clearedAmount = Some(BigDecimal(0)),
            items = Some(Seq.empty)
          )

        val financialTransactions = Seq(
          financialTransaction,
          financialTransaction.copy(
            taxPeriodTo = Some(period2.firstDay),
            taxPeriodFrom = Some(period2.firstDay)
          )
        )

        val vatReturn = arbitrary[VatReturn].sample.value.copy(vrn, period = period)

        when(appConfig.strategicReturnApiEnabled) `thenReturn` true
        when(periodService.getPeriodYears(any())) `thenReturn` Seq(periodYear2021)
        when(financialDataConnector.getFinancialData(any(), equalTo(queryParameters2021))) `thenReturn`
          Future.successful(
            Right(Some(FinancialData(
              Some("VRN"),
              Some("123456789"),
              Some("ECOM"),
              ZonedDateTime.now(),
              Option(financialTransactions)
            )))
          )
        when(vatReturnConnector.getObligations(any(), any())) `thenReturn`
          Future.successful(Right(EtmpObligations(Seq(EtmpObligation(Seq(
            EtmpObligationDetails(Fulfilled, etmpVatReturn.periodKey),
            EtmpObligationDetails(Fulfilled, toEtmpPeriodString(period2)),
          ))))))

        val response = financialDataService.getVatReturnWithFinancialData(Vrn("123456789"), commencementDate).futureValue
        val expectedResponse =
          Seq(
            PeriodWithFinancialData(
              vatReturn.period,
              Some(Charge(period, BigDecimal(1000), BigDecimal(1000), BigDecimal(0))),
              1000,
              true
            ),
            PeriodWithFinancialData(
              period2,
              Some(Charge(period2, BigDecimal(1000), BigDecimal(1000), BigDecimal(0))),
              1000,
              true
            )
          )

        response must contain theSameElementsAs expectedResponse
        verify(financialDataConnector, times(1)).getFinancialData(any(), eqTo(queryParameters2021))
        verify(periodService, times(1)).getPeriodYears(eqTo(commencementDate))
        verify(vatReturnConnector, times(1)).getObligations(any(), any())
        verifyNoInteractions(vatReturnService)
        verifyNoInteractions(correctionsService)
      }

      "must return multiple VatReturnWithFinancialDatas when there is multiple vatReturns in the different period years and charges" in {
        val commencementDate = LocalDate.now()
        val period = StandardPeriod(2021, Q4)
        val period2 = StandardPeriod(2022, Q1)

        val financialTransaction =
          FinancialTransaction(
            chargeType = Some("G Ret AT EU-OMS"),
            mainType = None,
            taxPeriodFrom = Some(period.firstDay),
            taxPeriodTo = Some(period.lastDay),
            originalAmount = Some(BigDecimal(1000)),
            outstandingAmount = Some(BigDecimal(1000)),
            clearedAmount = Some(BigDecimal(0)),
            items = Some(Seq.empty)
          )

        val financialTransaction2 =
          financialTransaction.copy(
            taxPeriodFrom = Some(period2.firstDay),
            taxPeriodTo = Some(period2.lastDay)
          )

        val financialTransactions = Seq(financialTransaction)
        val financialTransactions2 = Seq(financialTransaction2)

        val etmpVatReturn = arbitrary[EtmpVatReturn].sample.value.copy(periodKey = toEtmpPeriodString(period))
        val periodYear2 = PeriodYear(2022)
        val queryParameters2 =
          FinancialDataQueryParameters(fromDate = Some(periodYear2.startOfYear), toDate = Some(periodYear2.endOfYear))

        when(appConfig.strategicReturnApiEnabled) `thenReturn` true
        when(periodService.getPeriodYears(any())) `thenReturn` Seq(periodYear2021, periodYear2)
        when(financialDataConnector.getFinancialData(any(), any()))
          .thenReturn(
            Future.successful(
              Right(Some(FinancialData(
                Some("VRN"),
                Some("123456789"),
                Some("ECOM"),
                ZonedDateTime.now(),
                Option(financialTransactions)
              )))
            )
          ).thenReturn(
            Future.successful(
              Right(Some(FinancialData(
                Some("VRN"),
                Some("123456789"),
                Some("ECOM"),
                ZonedDateTime.now(),
                Option(financialTransactions2)
              )))
            )
          )
        when(vatReturnConnector.getObligations(any(), any())) `thenReturn`
          Future.successful(Right(EtmpObligations(Seq(EtmpObligation(Seq(
            EtmpObligationDetails(Fulfilled, toEtmpPeriodString(period)),
            EtmpObligationDetails(Fulfilled, toEtmpPeriodString(period2)),
          ))))))
        when(vatReturnConnector.get(any(), any())) `thenReturn`
          Future.successful(Right(etmpVatReturn))

        val response = financialDataService.getVatReturnWithFinancialData(Vrn("123456789"), commencementDate).futureValue
        val expectedResponse =
          Seq(
            PeriodWithFinancialData(
              period,
              Some(Charge(period, BigDecimal(1000), BigDecimal(1000), BigDecimal(0))),
              1000,
              true
            ),
            PeriodWithFinancialData(
              period2,
              Some(Charge(period2, BigDecimal(1000), BigDecimal(1000), BigDecimal(0))),
              1000,
              true
            )
          )

        response must contain theSameElementsAs expectedResponse
        verify(financialDataConnector, times(1)).getFinancialData(any(), eqTo(queryParameters2021))
        verify(financialDataConnector, times(1)).getFinancialData(any(), eqTo(queryParameters2))
        verify(periodService, times(1)).getPeriodYears(eqTo(commencementDate))
        verify(vatReturnConnector, times(1)).getObligations(any(), any())
        verifyNoInteractions(vatReturnService)
        verifyNoInteractions(correctionsService)
      }

      "must return one VatReturnWithFinancialData with no charge when there is one vatReturn and no charge with correction" in {
        val commencementDate = LocalDate.now()

        when(appConfig.strategicReturnApiEnabled) `thenReturn` true
        when(financialDataConnector.getFinancialData(any(), eqTo(queryParameters2021))) `thenReturn` Future.successful(Right(None))
        when(vatReturnConnector.getObligations(any(), any())) `thenReturn` Future.successful(Right(etmpObligations))
        when(vatReturnConnector.get(any(), any())) `thenReturn` Future.successful(Right(etmpVatReturn))
        when(periodService.getPeriodYears(any())) `thenReturn` Seq(periodYear2021)
        when(correctionsService.get(any(), any())) `thenReturn` Future.successful(Some(correctionPayload))
        when(vatReturnSalesService.getTotalVatOnSalesAfterCorrection(any(), any())) `thenReturn` BigDecimal(100)
        val expectedResponse =
          Seq(PeriodWithFinancialData(vatReturn.period, None, etmpVatReturn.totalVATAmountDueForAllMSGBP, true))

        val response = financialDataService.getVatReturnWithFinancialData(Vrn("123456789"), commencementDate).futureValue

        response mustBe expectedResponse
        verify(financialDataConnector, times(1)).getFinancialData(any(), eqTo(queryParameters2021))
        verify(vatReturnConnector, times(1)).getObligations(any(), any())
        verify(vatReturnConnector, times(1)).get(eqTo(Vrn("123456789")), any())
        verify(periodService, times(1)).getPeriodYears(eqTo(commencementDate))
        verifyNoInteractions(vatReturnService)
        verifyNoInteractions(correctionsService)
      }
    }
  }

  ".filterIfPaymentIsOutstanding" - {

    val fullyPaidCharge = Charge(
      period = StandardPeriod(2021, Q3),
      originalAmount = BigDecimal(1000),
      outstandingAmount = BigDecimal(0),
      clearedAmount = BigDecimal(1000)
    )
    val notPaidCharge = Charge(
      period = StandardPeriod(2021, Q3),
      originalAmount = BigDecimal(1000),
      outstandingAmount = BigDecimal(1000),
      clearedAmount = BigDecimal(0)
    )

    "when passing one vatReturnWithFinancialData" - {

      val vatReturnWithFinancialData = PeriodWithFinancialData(vatReturn.period, None, 0, true)

      "should return one vatReturnWithFinancialData" - {

        "when no charge exists and has vat owed with no correction" in {
          val result = financialDataService.filterIfPaymentIsOutstanding(Seq(vatReturnWithFinancialData))

          result mustBe Seq(vatReturnWithFinancialData)
        }

        "when no charge exists and has vat owed with correction" in {
          val vatReturnWithFinancialData = PeriodWithFinancialData(vatReturn.period, None, 0, true)

          val result =
            financialDataService.filterIfPaymentIsOutstanding(
              Seq(vatReturnWithFinancialData)
            )

          result mustBe Seq(vatReturnWithFinancialData)
        }

        "when charge exists with outstanding amount" in {
          val vatReturnWithFinancialData = PeriodWithFinancialData(vatReturn.period, Some(notPaidCharge), 0, true)
          val result = financialDataService.filterIfPaymentIsOutstanding(Seq(vatReturnWithFinancialData))

          result mustBe Seq(vatReturnWithFinancialData)
        }
      }
    }

    "when passing vatReturnWithFinancialDatas" - {

      "should return empty when no outstanding amounts" in {
        val vatReturn2 = arbitrary[VatReturn].sample.value
        val vatReturnWithFinancialData = PeriodWithFinancialData(vatReturn.period, Some(fullyPaidCharge), 0L, true)
        val vatReturnWithFinancialData2 = PeriodWithFinancialData(vatReturn2.period, Some(fullyPaidCharge), 0L, true)

        financialDataService.filterIfPaymentIsOutstanding(
          Seq(vatReturnWithFinancialData, vatReturnWithFinancialData2)
        ) mustBe Seq.empty
      }

      "should return all vatReturnWithFinancialDatas with outstanding amounts" in {
        val vatReturn2 = arbitrary[VatReturn].sample.value
        val vatReturnWithFinancialData = PeriodWithFinancialData(vatReturn.period, Some(notPaidCharge), 1000L, true)
        val vatReturnWithFinancialData2 = PeriodWithFinancialData(vatReturn2.period, Some(notPaidCharge), 1000L, true)

        financialDataService.filterIfPaymentIsOutstanding(
          Seq(vatReturnWithFinancialData, vatReturnWithFinancialData2)
        ) mustBe Seq(vatReturnWithFinancialData, vatReturnWithFinancialData2)
      }
    }

    "return empty when" - {

      "charge has been fully paid" in {
        val vatReturnWithFinancialData = PeriodWithFinancialData(vatReturn.period, Some(fullyPaidCharge), 0, true)

        val result = financialDataService.filterIfPaymentIsOutstanding(Seq(vatReturnWithFinancialData))

        result mustBe Seq.empty
      }

      "no charge exists and does not have vat owed" in {
        val vatReturnWithFinancialData = PeriodWithFinancialData(vatReturn.period, Some(fullyPaidCharge), 0, true)

        val result = financialDataService.filterIfPaymentIsOutstanding(Seq(vatReturnWithFinancialData))

        result mustBe Seq.empty
      }
    }
  }

}