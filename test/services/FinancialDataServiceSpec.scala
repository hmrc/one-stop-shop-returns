package services

import connectors.FinancialDataConnector
import generators.Generators
import models._
import models.financialdata._
import models.Quarter._
import org.mockito.ArgumentMatchers.{any, eq => equalTo}
import org.mockito.Mockito.when
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import uk.gov.hmrc.domain.Vrn

import java.time.{Clock, LocalDate, ZonedDateTime, ZoneId}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FinancialDataServiceSpec extends AnyFreeSpec
  with Matchers
  with MockitoSugar
  with ScalaCheckPropertyChecks
  with Generators
  with OptionValues
  with ScalaFutures {

  val stubClock: Clock = Clock.fixed(LocalDate.now.atStartOfDay(ZoneId.systemDefault).toInstant, ZoneId.systemDefault)
  val stubPeriodService: PeriodService = mock[PeriodService]

  "getFinancialData" - {

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

    "when commencement date is in Q3 2021" in {
      val commencementDate = LocalDate.of(2021, 9, 1)
      val connector = mock[FinancialDataConnector]
      val vatReturnService = mock[VatReturnService]
      val service = new FinancialDataService(connector, vatReturnService, stubPeriodService, stubClock)
      val periodYear = PeriodYear(2021)
      val queryParameters = FinancialDataQueryParameters(fromDate = Some(periodYear.startOfYear), toDate = Some(periodYear.endOfYear))

      when(stubPeriodService.getPeriodYears(any())) thenReturn
        Seq(periodYear)
      when(connector.getFinancialData(any(), equalTo(queryParameters))) thenReturn
        Future.successful(Right(Some(FinancialData(Some("VRN"), Some("123456789"), Some("ECOM"), ZonedDateTime.now(), Option(financialTransactions)))))

      val response = service.getFinancialData(Vrn("123456789"), commencementDate).futureValue

      response.isDefined mustBe true
      val financialData = response.get
      financialData.idType mustBe Some("VRN")
    }
  }

  ".getCharge" - {

    "return a charge" - {

      "when there has been no payments" in {

        val period = Period(2021, Q3)
        val periodYear = PeriodYear(2021)

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

        val connector = mock[FinancialDataConnector]
        val vatReturnService = mock[VatReturnService]
        val service = new FinancialDataService(connector, vatReturnService, stubPeriodService, stubClock)
        val queryParameters = FinancialDataQueryParameters(fromDate = Some(period.firstDay), toDate = Some(period.lastDay))

        when(stubPeriodService.getPeriodYears(any())) thenReturn
          Seq(periodYear)
        when(connector.getFinancialData(any(), equalTo(queryParameters))) thenReturn (
          Future.successful(Right(Some(FinancialData(Some("VRN"), Some("123456789"), Some("ECOM"), ZonedDateTime.now(), Option(financialTransactions))))))

        val response = service.getCharge(Vrn("123456789"), period).futureValue

        response.isDefined mustBe true
        val charge = response.get
        charge.period mustBe period
        charge.originalAmount mustBe BigDecimal(1000)
        charge.outstandingAmount mustBe BigDecimal(1000)
        charge.clearedAmount mustBe BigDecimal(0)

      }

      "when there has been a payment and a single transaction" in {

        val period = Period(2021, Q3)
        val periodYear = PeriodYear(2021)

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

        val connector = mock[FinancialDataConnector]
        val vatReturnService = mock[VatReturnService]
        val service = new FinancialDataService(connector, vatReturnService, stubPeriodService, stubClock)
        val queryParameters = FinancialDataQueryParameters(fromDate = Some(period.firstDay), toDate = Some(period.lastDay))

        when(stubPeriodService.getPeriodYears(any())) thenReturn
          Seq(periodYear)
        when(connector.getFinancialData(any(), equalTo(queryParameters))) thenReturn (
          Future.successful(Right(Some(FinancialData(Some("VRN"), Some("123456789"), Some("ECOM"), ZonedDateTime.now(), Option(financialTransactions))))))

        val response = service.getCharge(Vrn("123456789"), period).futureValue

        response.isDefined mustBe true
        val charge = response.get
        charge.period mustBe period
        charge.originalAmount mustBe BigDecimal(1000)
        charge.outstandingAmount mustBe BigDecimal(500)
        charge.clearedAmount mustBe BigDecimal(500)

      }

      "when there has been two transactions and two payments" in {

        val period = Period(2021, Q3)
        val periodYear = PeriodYear(2021)

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

        val connector = mock[FinancialDataConnector]
        val vatReturnService = mock[VatReturnService]
        val service = new FinancialDataService(connector, vatReturnService, stubPeriodService, stubClock)
        val queryParameters = FinancialDataQueryParameters(fromDate = Some(period.firstDay), toDate = Some(period.lastDay))

        when(stubPeriodService.getPeriodYears(any())) thenReturn
          Seq(periodYear)
        when(connector.getFinancialData(any(), equalTo(queryParameters))) thenReturn (
          Future.successful(Right(Some(FinancialData(Some("VRN"), Some("123456789"), Some("ECOM"), ZonedDateTime.now(), Option(financialTransactions))))))

        val response = service.getCharge(Vrn("123456789"), period).futureValue

        response.isDefined mustBe true
        val charge = response.get
        charge.period mustBe period
        charge.originalAmount mustBe BigDecimal(2500)
        charge.outstandingAmount mustBe BigDecimal(1000)
        charge.clearedAmount mustBe BigDecimal(1500)

      }

    }

  }

  ".getOutstandingAmounts" - {

    "return a period with outstanding amounts" - {

      "when there has been no payments for 1 period" in {

        val period = Period(2021, Q3)
        val periodYear = PeriodYear(2021)
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

        val connector = mock[FinancialDataConnector]
        val vatReturnService = mock[VatReturnService]
        val service = new FinancialDataService(connector, vatReturnService, stubPeriodService, stubClock)
        val queryParameters = FinancialDataQueryParameters(fromDate = Some(periodYear.startOfYear), toDate = Some(periodYear.endOfYear), onlyOpenItems = Some(true))

        when(vatReturnService.get(any())) thenReturn Future.successful(Seq(vatReturn))
        when(connector.getFinancialData(any(), equalTo(queryParameters))) thenReturn(
          Future.successful(Right(Some(FinancialData(Some("VRN"), Some("123456789"), Some("ECOM"), ZonedDateTime.now(stubClock), Option(financialTransactions))))))

        val response = service.getOutstandingAmounts(Vrn("123456789")).futureValue

        val expectedResponse = Seq(PeriodWithOutstandingAmount(period, BigDecimal(1000)))

        response must contain theSameElementsAs expectedResponse
      }

    }
    "return empty" - {

      "when there are no transactions found" in {

        val period = Period(2021, Q3)
        val periodYear = PeriodYear(2021)
        val vatReturn = arbitrary[VatReturn].sample.value.copy(period = period)

        val financialTransactions = Seq.empty

        val connector = mock[FinancialDataConnector]
        val vatReturnService = mock[VatReturnService]
        val service = new FinancialDataService(connector, vatReturnService, stubPeriodService, stubClock)
        val queryParameters = FinancialDataQueryParameters(fromDate = Some(periodYear.startOfYear), toDate = Some(periodYear.endOfYear), onlyOpenItems = Some(true))

        when(vatReturnService.get(any())) thenReturn Future.successful(Seq(vatReturn))
        when(connector.getFinancialData(any(), equalTo(queryParameters))) thenReturn(
          Future.successful(Right(Some(FinancialData(Some("VRN"), Some("123456789"), Some("ECOM"), ZonedDateTime.now(stubClock), Option(financialTransactions))))))

        val response = service.getOutstandingAmounts(Vrn("123456789")).futureValue

        val expectedResponse = Seq.empty

        response must contain theSameElementsAs expectedResponse
      }


      "when there are no completed returns" in {

        val connector = mock[FinancialDataConnector]
        val vatReturnService = mock[VatReturnService]
        val service = new FinancialDataService(connector, vatReturnService, stubPeriodService, stubClock)

        when(vatReturnService.get(any())) thenReturn Future.successful(Seq.empty)

        val response = service.getOutstandingAmounts(Vrn("123456789")).futureValue

        val expectedResponse = Seq.empty

        response must contain theSameElementsAs expectedResponse
      }

    }

  }

  ".getVatReturnWithFinancialData" - {

    "return a VatReturnWithFinancialData" - {

      "when there has been no payments for 1 period" in {

        val commencementDate = LocalDate.now()
        val period = Period(2021, Q3)

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

        val vatReturn = arbitrary[VatReturn].sample.value.copy(period = period)

        val connector = mock[FinancialDataConnector]
        val vatReturnService = mock[VatReturnService]
        val service = new FinancialDataService(connector, vatReturnService, stubPeriodService, stubClock)
        val queryParameters = FinancialDataQueryParameters(fromDate = Some(commencementDate), toDate = Some(LocalDate.now(stubClock)))

        when(connector.getFinancialData(any(), equalTo(queryParameters))) thenReturn (
          Future.successful(Right(Some(FinancialData(Some("VRN"), Some("123456789"), Some("ECOM"), ZonedDateTime.now(stubClock), Option(financialTransactions))))))

        when(vatReturnService.get(any())) thenReturn Future.successful(Seq(vatReturn))

        val response = service.getVatReturnWithFinancialData(Vrn("123456789"), commencementDate).futureValue

        val expectedResponse = Seq(VatReturnWithFinancialData(vatReturn, Some(Charge(period, BigDecimal(1000), BigDecimal(1000), BigDecimal(0))), Some(100000)))

        response must contain theSameElementsAs expectedResponse
      }

    }

  }
}
