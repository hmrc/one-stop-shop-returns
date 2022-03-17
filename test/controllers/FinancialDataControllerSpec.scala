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
import models.financialdata.{Charge, CurrentPayments, Payment, PeriodWithOutstandingAmount, VatReturnWithFinancialData}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalacheck.Arbitrary.arbitrary
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
  val mockFinancialDataService = mock[FinancialDataService]
  val mockVatReturnSalesService = mock[VatReturnSalesService]
  val mockRegistrationConnector = mock[RegistrationConnector]

  override def beforeEach(): Unit = {
    Mockito.reset(mockFinancialDataService)
    Mockito.reset(mockVatReturnSalesService)
    Mockito.reset(mockRegistrationConnector)
    super.beforeEach()
  }

  ".getCharge(period)" - {

    val period = Period(2021, Q3)
    val charge = Charge(period, 1000, 500, 500)

    lazy val request =
      FakeRequest(GET, routes.FinancialDataController.getCharge(period).url)

    "return a basic charge" in {
      val financialDataService = mock[FinancialDataService]

      val app =
        applicationBuilder
          .overrides(bind[FinancialDataService].to(financialDataService))
          .build()

      when(financialDataService.getCharge(any(), any())) thenReturn Future.successful(Some(charge))

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

      when(financialDataService.getCharge(any(), any())) thenReturn Future.successful(None)

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

      when(financialDataService.getCharge(any(), any())) thenReturn Future.failed(DesException("Some exception"))

      running(app) {

        val result = route(app, request).value

        whenReady(result.failed) { exp => exp mustBe a[Exception] }
      }
    }

  }

  ".getOutstandingAmounts" - {

    val period = Period(2021, Q3)
    val outstandingPayment = PeriodWithOutstandingAmount(period, BigDecimal(1000.50))

    lazy val request =
      FakeRequest(GET, routes.FinancialDataController.getOutstandingAmounts().url)

    "return a basic outstanding payment" in {
      val financialDataService = mock[FinancialDataService]

      val app =
        applicationBuilder
          .overrides(bind[FinancialDataService].to(financialDataService))
          .build()

      when(financialDataService.getOutstandingAmounts(any())) thenReturn Future.successful(Seq(outstandingPayment))

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

      when(financialDataService.getOutstandingAmounts(any())) thenReturn Future.failed(DesException("Some exception"))

      running(app) {

        val result = route(app, request).value

        whenReady(result.failed) { exp => exp mustBe a[Exception] }
      }
    }


  }

  ".getVatReturnWithFinancialData(commencementDate)" - {

    val period = Period(2021, Q3)
    val vatReturn = arbitrary[VatReturn].sample.value
    val charge = Charge(period, 1000, 500, 500)
    val vatOwed = 1000
    val commencementDate = LocalDate.now()

    val vatReturnWithFinancialData = VatReturnWithFinancialData(vatReturn, Some(charge), vatOwed, None)

    lazy val request =
      FakeRequest(GET, routes.FinancialDataController.getVatReturnWithFinancialData(commencementDate).url)

    "return a basic vat return with financial data" in {
      val financialDataService = mock[FinancialDataService]

      val app =
        applicationBuilder
          .overrides(bind[FinancialDataService].to(financialDataService))
          .build()

      when(financialDataService.getVatReturnWithFinancialData(any(), any())) thenReturn Future.successful(Seq(vatReturnWithFinancialData))

      running(app) {

        val result = route(app, request).value

        status(result) mustEqual OK
        contentAsJson(result) mustBe Json.toJson(Seq(vatReturnWithFinancialData))
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
      val periodDue = Period(2021, Q4)
      val periodOverdue = Period(2021, Q3)
      val charge1 = Charge(periodDue, 1000, 1000, 0)
      val charge2 = Charge(periodOverdue, 1000, 500, 500)

      val vatReturnWithFinancialData1 = VatReturnWithFinancialData(
        completeVatReturn.copy(period = periodDue), Some(charge1), 1000L, None)
      val vatReturnWithFinancialData2 = VatReturnWithFinancialData(
        completeVatReturn.copy(period = periodOverdue), Some(charge2), 1000L, None)

      val payment1 = Payment.fromVatReturnWithFinancialData(vatReturnWithFinancialData1)
      val payment2 = Payment.fromVatReturnWithFinancialData(vatReturnWithFinancialData2)

      when(mockFinancialDataService.getVatReturnWithFinancialData(any(), any())) thenReturn Future.successful(Seq(vatReturnWithFinancialData1, vatReturnWithFinancialData2))
      when(mockFinancialDataService.filterIfPaymentIsOutstanding(any())) thenReturn Seq(vatReturnWithFinancialData1, vatReturnWithFinancialData2)
      when(mockVatReturnSalesService.getTotalVatOnSalesAfterCorrection(any(), any())) thenReturn BigDecimal(0)
      when(mockRegistrationConnector.getRegistration(any())) thenReturn Future.successful(Some(RegistrationData.registration))
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
        contentAsJson(result) mustBe Json.toJson(CurrentPayments(Seq(payment1), Seq(payment2)))
      }
    }

    "must return Current Payment as Json when there are due payments" in {

      val stubClock = Clock.fixed(
        Instant.from(LocalDate.of(2022, 1, 1)
          .atStartOfDay(ZoneId.systemDefault())), ZoneId.systemDefault())
      val periodDue = Period(2021, Q4)
      val charge1 = Charge(periodDue, 1000, 1000, 0)

      val vatReturnWithFinancialData = VatReturnWithFinancialData(
        completeVatReturn.copy(period = periodDue), Some(charge1), 1000L, None)

      val payment = Payment.fromVatReturnWithFinancialData(vatReturnWithFinancialData)

      when(mockFinancialDataService.getVatReturnWithFinancialData(any(), any())) thenReturn Future.successful(Seq(vatReturnWithFinancialData))
      when(mockFinancialDataService.filterIfPaymentIsOutstanding(any())) thenReturn Seq(vatReturnWithFinancialData)
      when(mockVatReturnSalesService.getTotalVatOnSalesAfterCorrection(any(), any())) thenReturn BigDecimal(0)
      when(mockRegistrationConnector.getRegistration(any())) thenReturn Future.successful(Some(RegistrationData.registration))

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
        contentAsJson(result) mustBe Json.toJson(CurrentPayments(Seq(payment), Seq.empty))
      }
    }

    "must return Current Payments as Json when there are overdue payments" in {

      val stubClock = Clock.fixed(
        Instant.from(LocalDate.of(2022, 4, 1)
          .atStartOfDay(ZoneId.systemDefault())), ZoneId.systemDefault())
      val periodOverdue1 = Period(2021, Q3)
      val periodOverdue2 = Period(2021, Q4)
      val charge1 = Charge(periodOverdue1, 1000, 1000, 0)
      val charge2 = Charge(periodOverdue2, 1000, 1000, 0)

      val vatReturnWithFinancialData1 = VatReturnWithFinancialData(
        completeVatReturn.copy(period = periodOverdue1), Some(charge1), 1000L, None)
      val vatReturnWithFinancialData2 = VatReturnWithFinancialData(
        completeVatReturn.copy(period = periodOverdue2), Some(charge2), 1000L, None)

      val payment1 = Payment.fromVatReturnWithFinancialData(vatReturnWithFinancialData1)
      val payment2 = Payment.fromVatReturnWithFinancialData(vatReturnWithFinancialData2)

      when(mockFinancialDataService.getVatReturnWithFinancialData(any(), any())) thenReturn Future.successful(Seq(vatReturnWithFinancialData1, vatReturnWithFinancialData2))
      when(mockFinancialDataService.filterIfPaymentIsOutstanding(any())) thenReturn Seq(vatReturnWithFinancialData1, vatReturnWithFinancialData2)
      when(mockVatReturnSalesService.getTotalVatOnSalesAfterCorrection(any(), any())) thenReturn BigDecimal(0)
      when(mockRegistrationConnector.getRegistration(any())) thenReturn Future.successful(Some(RegistrationData.registration))

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
        contentAsJson(result) mustBe Json.toJson(CurrentPayments(Seq.empty, Seq(payment1, payment2)))
      }
    }

    "must return Not Found if no registration is found for VRN" in {

      when(mockRegistrationConnector.getRegistration(any())) thenReturn Future.successful(None)

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
