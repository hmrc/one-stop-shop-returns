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
import generators.Generators
import models._
import models.Quarter.Q3
import models.financialdata.{Charge, PeriodWithOutstandingAmount}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.inject.bind
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.FinancialDataService

import java.time.LocalDate
import scala.concurrent.Future

class FinancialDataControllerSpec
  extends SpecBase
    with ScalaCheckPropertyChecks
    with Generators {

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

      when(financialDataService.getCharge(any(), any())) thenReturn Future.failed(new Exception("Some exception"))

      running(app) {

        val result = route(app, request).value

        whenReady(result.failed) { exp => exp mustBe a[Exception]}
      }
    }

  }

  ".getOutstandingAmounts(period)" - {

    val commencementDate = LocalDate.now(stubClock)
    val period = Period(2021, Q3)
    val outstandingPayment = PeriodWithOutstandingAmount(period, BigDecimal(1000.50))

    lazy val request =
      FakeRequest(GET, routes.FinancialDataController.getOutstandingAmounts(commencementDate).url)

    "return a basic outstanding payment" in {
      val financialDataService = mock[FinancialDataService]

      val app =
        applicationBuilder
          .overrides(bind[FinancialDataService].to(financialDataService))
          .build()

      when(financialDataService.getOutstandingAmounts(any(), any())) thenReturn Future.successful(Seq(outstandingPayment))

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

      when(financialDataService.getOutstandingAmounts(any(), any())) thenReturn Future.failed(new Exception("Some exception"))

      running(app) {

        val result = route(app, request).value

        whenReady(result.failed) { exp => exp mustBe a[Exception]}
      }
    }


  }
}
