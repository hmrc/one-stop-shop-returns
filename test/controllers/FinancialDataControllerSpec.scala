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
import controllers.actions.FakeFailingAuthConnector
import generators.Generators
import models._
import models.requests.VatReturnRequest
import models.Quarter.Q3
import models.financialdata.Charge
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{times, verify, when}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{FinancialDataService, VatReturnService}
import uk.gov.hmrc.auth.core.{AuthConnector, MissingBearerToken}

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

    "return NotFound if no charge found" in {
      val financialDataService = mock[FinancialDataService]

      val app =
        applicationBuilder
          .overrides(bind[FinancialDataService].to(financialDataService))
          .build()

      when(financialDataService.getCharge(any(), any())) thenReturn Future.successful(None)

      running(app) {

        val result = route(app, request).value

        status(result) mustEqual NOT_FOUND
      }
    }

  }
}
