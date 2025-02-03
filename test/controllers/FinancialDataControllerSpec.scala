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

package controllers

import base.SpecBase
import connectors.RegistrationConnector
import generators.Generators
import models._
import models.Quarter.{Q3, Q4}
import models.des.DesException
import models.exclusions.{ExcludedTrader, ExclusionReason}
import models.financialdata._
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalacheck.Gen
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.inject.bind
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{FinancialDataService, VatReturnSalesService}
import testutils.RegistrationData
import uk.gov.hmrc.domain.Vrn

import java.time.{Clock, Instant, LocalDate, ZoneId}
import scala.concurrent.Future

class FinancialDataControllerSpec
  extends SpecBase
    with ScalaCheckPropertyChecks
    with Generators
    with BeforeAndAfterEach {

  private val authorisedVrn = Vrn("123456789")
  private val notAuthorisedVrn = arbitraryVrn.arbitrary.retryUntil(_ != authorisedVrn).sample.value
  private val mockFinancialDataService = mock[FinancialDataService]
  private val mockVatReturnSalesService = mock[VatReturnSalesService]
  private val mockRegistrationConnector = mock[RegistrationConnector]

  override def beforeEach(): Unit = {
    Mockito.reset(mockFinancialDataService)
    Mockito.reset(mockVatReturnSalesService)
    Mockito.reset(mockRegistrationConnector)
    super.beforeEach()
  }

  ".getCharge(period)" - {

    val period = StandardPeriod(2021, Q3)
    val charge = Charge(period, 1000, 500, 500)

    lazy val request =
      FakeRequest(GET, routes.FinancialDataController.getCharge(period).url)

    "return a basic charge" in {
      val financialDataService = mock[FinancialDataService]

      val app =
        applicationBuilder
          .overrides(bind[FinancialDataService].to(financialDataService))
          .build()

      when(financialDataService.getCharge(any(), any())) `thenReturn` Future.successful(Some(charge))

      running(app) {

        val result = route(app, request).value

        status(result) mustEqual OK
        contentAsJson(result) mustBe Json.toJson(charge)
      }
    }

    "return OK if no charge found" in {
      val financialDataService = mock[FinancialDataService]

      val app =
        applicationBuilder
          .overrides(bind[FinancialDataService].to(financialDataService))
          .build()

      when(financialDataService.getCharge(any(), any())) `thenReturn` Future.successful(None)

      running(app) {

        val result = route(app, request).value

        status(result) mustEqual OK
      }
    }

    "error if api errors" in {
      val financialDataService = mock[FinancialDataService]

      val app =
        applicationBuilder
          .overrides(bind[FinancialDataService].to(financialDataService))
          .build()

      when(financialDataService.getCharge(any(), any())) `thenReturn` Future.failed(DesException("Some exception"))

      running(app) {

        val result = route(app, request).value

        whenReady(result.failed) { exp => exp mustBe a[Exception] }
      }
    }

  }

  ".getOutstandingAmounts" - {

    val period = StandardPeriod(2021, Q3)
    val outstandingPayment = PeriodWithOutstandingAmount(period, BigDecimal(1000.50))

    lazy val request =
      FakeRequest(GET, routes.FinancialDataController.getOutstandingAmounts().url)

    "return a basic outstanding payment" in {
      val financialDataService = mock[FinancialDataService]

      val app =
        applicationBuilder
          .overrides(bind[FinancialDataService].to(financialDataService))
          .build()

      when(financialDataService.getOutstandingAmounts(any())) `thenReturn` Future.successful(Seq(outstandingPayment))

      running(app) {

        val result = route(app, request).value

        status(result) mustEqual OK
        contentAsJson(result) mustBe Json.toJson(Seq(outstandingPayment))
      }
    }

    "error if api errors" in {
      val financialDataService = mock[FinancialDataService]

      val app =
        applicationBuilder
          .overrides(bind[FinancialDataService].to(financialDataService))
          .build()

      when(financialDataService.getOutstandingAmounts(any())) `thenReturn` Future.failed(DesException("Some exception"))

      running(app) {

        val result = route(app, request).value

        whenReady(result.failed) { exp => exp mustBe a[Exception] }
      }
    }


  }

  ".prepareFinancialData" - {

    lazy val request =
      FakeRequest(GET, routes.FinancialDataController.prepareFinancialData(authorisedVrn.vrn).url)

    "must return both Current Payments as Json when there are due payments and overdue payments" in {

      val stubClock = Clock.fixed(
        Instant.from(LocalDate.of(2022, 1, 1)
          .atStartOfDay(ZoneId.systemDefault())), ZoneId.systemDefault())
      val periodDue = StandardPeriod(2021, Q4)
      val periodOverdue = StandardPeriod(2021, Q3)
      val charge1 = Charge(periodDue, 1000, 1000, 0)
      val charge2 = Charge(periodOverdue, 1000, 500, 500)
      val completedCharge = Charge(periodOverdue.getPreviousPeriod, 1000, 0, 1000)

      val vatReturnWithFinancialData1 = PeriodWithFinancialData(
        periodDue, Some(charge1), 1000L, true)
      val vatReturnWithFinancialData2 = PeriodWithFinancialData(
        periodOverdue, Some(charge2), 1000L, true)
      val completedVatReturnWithFinancialData = PeriodWithFinancialData(
        periodOverdue.getPreviousPeriod, Some(completedCharge), 0, true)

      val payment1 = Payment.fromVatReturnWithFinancialData(vatReturnWithFinancialData1, None, stubClock)
      val payment2 = Payment.fromVatReturnWithFinancialData(vatReturnWithFinancialData2, None, stubClock)
      val completedPayment = Payment.fromVatReturnWithFinancialData(completedVatReturnWithFinancialData, None, stubClock)

      when(mockFinancialDataService.getVatReturnWithFinancialData(any(), any())) `thenReturn` Future.successful(Seq(vatReturnWithFinancialData1, vatReturnWithFinancialData2))
      when(mockFinancialDataService.filterIfPaymentIsOutstanding(any())) `thenReturn` Seq(vatReturnWithFinancialData1, vatReturnWithFinancialData2)
      when(mockFinancialDataService.filterIfPaymentIsComplete(any())) `thenReturn` Seq(completedVatReturnWithFinancialData)
      when(mockVatReturnSalesService.getTotalVatOnSalesAfterCorrection(any(), any())) `thenReturn` BigDecimal(0)
      when(mockRegistrationConnector.getRegistration(any())(any())) `thenReturn` Future.successful(Some(RegistrationData.registration))
      val app =
        applicationBuilder
          .overrides(bind[FinancialDataService].to(mockFinancialDataService))
          .overrides(bind[VatReturnSalesService].to(mockVatReturnSalesService))
          .overrides(bind[RegistrationConnector].to(mockRegistrationConnector))
          .overrides(bind[Clock].toInstance(stubClock))
          .build()

      running(app) {

        val result = route(app, request).value

        status(result) mustBe OK
        contentAsJson(result) mustBe Json.toJson(CurrentPayments(Seq(payment1), Seq(payment2), Seq.empty, Seq(completedPayment), payment1.amountOwed + payment2.amountOwed, payment1.amountOwed))
      }
    }

    "must return Current Payment as Json when there are due payments" in {

      val stubClock = Clock.fixed(
        Instant.from(LocalDate.of(2022, 1, 1)
          .atStartOfDay(ZoneId.systemDefault())), ZoneId.systemDefault())
      val periodDue = StandardPeriod(2021, Q4)
      val charge1 = Charge(periodDue, 1000, 1000, 0)

      val vatReturnWithFinancialData = PeriodWithFinancialData(
        periodDue, Some(charge1), 1000L, true)

      val payment = Payment.fromVatReturnWithFinancialData(vatReturnWithFinancialData, None, stubClock)

      when(mockFinancialDataService.getVatReturnWithFinancialData(any(), any())) `thenReturn` Future.successful(Seq(vatReturnWithFinancialData))
      when(mockFinancialDataService.filterIfPaymentIsOutstanding(any())) `thenReturn` Seq(vatReturnWithFinancialData)
      when(mockFinancialDataService.filterIfPaymentIsComplete(any())) `thenReturn` Seq.empty
      when(mockVatReturnSalesService.getTotalVatOnSalesAfterCorrection(any(), any())) `thenReturn` BigDecimal(0)
      when(mockRegistrationConnector.getRegistration(any())(any())) `thenReturn` Future.successful(Some(RegistrationData.registration))

      val app =
        applicationBuilder
          .overrides(bind[FinancialDataService].to(mockFinancialDataService))
          .overrides(bind[VatReturnSalesService].to(mockVatReturnSalesService))
          .overrides(bind[RegistrationConnector].to(mockRegistrationConnector))
          .overrides(bind[Clock].toInstance(stubClock))
          .build()

      running(app) {

        val result = route(app, request).value

        status(result) mustBe OK
        contentAsJson(result) mustBe Json.toJson(CurrentPayments(Seq(payment), Seq.empty, Seq.empty, Seq.empty, payment.amountOwed, BigDecimal(0)))
      }
    }

    "must return Current Payments as Json when there are overdue payments" in {

      val stubClock = Clock.fixed(
        Instant.from(LocalDate.of(2022, 4, 1)
          .atStartOfDay(ZoneId.systemDefault())), ZoneId.systemDefault())
      val periodOverdue1 = StandardPeriod(2021, Q3)
      val periodOverdue2 = StandardPeriod(2021, Q4)
      val charge1 = Charge(periodOverdue1, 1000, 1000, 0)
      val charge2 = Charge(periodOverdue2, 1000, 1000, 0)

      val vatReturnWithFinancialData1 = PeriodWithFinancialData(
        periodOverdue1, Some(charge1), 1000L, true)
      val vatReturnWithFinancialData2 = PeriodWithFinancialData(
        periodOverdue2, Some(charge2), 1000L, true)

      val payment1 = Payment.fromVatReturnWithFinancialData(vatReturnWithFinancialData1, None, stubClock)
      val payment2 = Payment.fromVatReturnWithFinancialData(vatReturnWithFinancialData2, None, stubClock)
      val total = payment1.amountOwed + payment2.amountOwed

      when(mockFinancialDataService.getVatReturnWithFinancialData(any(), any())) `thenReturn` Future.successful(Seq(vatReturnWithFinancialData1, vatReturnWithFinancialData2))
      when(mockFinancialDataService.filterIfPaymentIsOutstanding(any())) `thenReturn` Seq(vatReturnWithFinancialData1, vatReturnWithFinancialData2)
      when(mockFinancialDataService.filterIfPaymentIsComplete(any())) `thenReturn` Seq.empty
      when(mockVatReturnSalesService.getTotalVatOnSalesAfterCorrection(any(), any())) `thenReturn` BigDecimal(0)
      when(mockRegistrationConnector.getRegistration(any())(any())) `thenReturn` Future.successful(Some(RegistrationData.registration))

      val app =
        applicationBuilder
          .overrides(bind[FinancialDataService].to(mockFinancialDataService))
          .overrides(bind[VatReturnSalesService].to(mockVatReturnSalesService))
          .overrides(bind[RegistrationConnector].to(mockRegistrationConnector))
          .overrides(bind[Clock].toInstance(stubClock))
          .build()

      running(app) {

        val result = route(app, request).value

        status(result) mustBe OK
        contentAsJson(result) mustBe Json.toJson(CurrentPayments(Seq.empty, Seq(payment1, payment2), Seq.empty, Seq.empty, total, total))
      }
    }

    "must return Current Payments as Json when there are overdue and excluded payments" in {

      val exclusionReason = Gen.oneOf(ExclusionReason.values).sample.value

      val excludedTrader: ExcludedTrader = ExcludedTrader(
        RegistrationData.registration.vrn,
        exclusionReason,
        period.firstDay
      )

      val stubClock = Clock.fixed(
        Instant.from(LocalDate.of(2022, 4, 1)
          .atStartOfDay(ZoneId.systemDefault())), ZoneId.systemDefault())

      val periodOverdue1 = StandardPeriod(2021, Q3)
      val periodOverdue2 = StandardPeriod(2018, Q4)
      val charge1 = Charge(periodOverdue1, 1000, 1000, 0)
      val charge2 = Charge(periodOverdue2, 1000, 1000, 0)

      val vatReturnWithFinancialData1 = PeriodWithFinancialData(
        periodOverdue1, Some(charge1), 1000L, true)

      val vatReturnWithFinancialData2 = PeriodWithFinancialData(
        periodOverdue2, Some(charge2), 1000L, true)

      val expectedPayment1 = Payment.fromVatReturnWithFinancialData(vatReturnWithFinancialData1, Some(excludedTrader), stubClock)
      val expectedExcludedPayment2 = Payment.fromVatReturnWithFinancialData(vatReturnWithFinancialData2, Some(excludedTrader), stubClock)

      val total = expectedPayment1.amountOwed + expectedExcludedPayment2.amountOwed

      when(mockFinancialDataService.getVatReturnWithFinancialData(any(), any())) `thenReturn` Future.successful(Seq(vatReturnWithFinancialData1, vatReturnWithFinancialData2))
      when(mockFinancialDataService.filterIfPaymentIsOutstanding(any())) `thenReturn` Seq(vatReturnWithFinancialData1, vatReturnWithFinancialData2)
      when(mockFinancialDataService.filterIfPaymentIsComplete(any())) `thenReturn` Seq.empty
      when(mockVatReturnSalesService.getTotalVatOnSalesAfterCorrection(any(), any())) `thenReturn` BigDecimal(0)
      when(mockRegistrationConnector.getRegistration(any())(any())) `thenReturn` Future.successful(Some(RegistrationData.registration.copy(excludedTrader = Some(excludedTrader))))

      val app =
        applicationBuilder
          .overrides(bind[FinancialDataService].to(mockFinancialDataService))
          .overrides(bind[VatReturnSalesService].to(mockVatReturnSalesService))
          .overrides(bind[RegistrationConnector].to(mockRegistrationConnector))
          .overrides(bind[Clock].toInstance(stubClock))
          .build()

      running(app) {

        val result = route(app, request).value

        status(result) mustBe OK
        contentAsJson(result) mustBe Json.toJson(CurrentPayments(
          duePayments = Seq.empty,
          overduePayments = Seq(expectedPayment1),
          excludedPayments = Seq(expectedExcludedPayment2),
          completedPayments = Seq.empty,
          total,
          total
        ))
      }
    }

    "must return Not Found if no registration is found for VRN" in {

      when(mockRegistrationConnector.getRegistration(any())(any())) `thenReturn` Future.successful(None)

      val app =
        applicationBuilder
          .overrides(bind[RegistrationConnector].to(mockRegistrationConnector))
          .build()

      running(app) {

        val result = route(app, request).value

        status(result) mustBe NOT_FOUND
      }
    }

    "must return Unauthorised if VRNs in the URI and in the request do not match" in {

      val app =
        applicationBuilder
          .build()

      running(app) {

        val result = route(app, FakeRequest(GET, routes.FinancialDataController.prepareFinancialData(notAuthorisedVrn.vrn).url)).value

        status(result) mustBe UNAUTHORIZED
      }
    }
  }

}
