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

import generators.Generators
import models.InsertResult.{AlreadyExists, InsertSucceeded}
import models.requests.VatReturnRequest
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{times, verify, when}
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.VatReturnService

import scala.concurrent.Future

class VatReturnControllerSpec
  extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with ScalaCheckPropertyChecks
    with Generators
    with OptionValues {

  ".post" - {

    "must save a VAT return and respond with Created" in {

      val mockService = mock[VatReturnService]
      when(mockService.createVatReturn(any())) thenReturn Future.successful(InsertSucceeded)

      val app =
        new GuiceApplicationBuilder()
          .overrides(bind[VatReturnService].toInstance(mockService))
          .build()

      val vatReturnRequest = arbitrary[VatReturnRequest].sample.value

      running(app) {
        val request =
          FakeRequest(POST, routes.VatReturnController.post().url)
            .withJsonBody(Json.toJson(vatReturnRequest))

        val result = route(app, request).value

        status(result) mustEqual CREATED
        verify(mockService, times(1)).createVatReturn(eqTo(vatReturnRequest))
      }
    }

    "must respond with Conflict when trying to save a duplicate" in {

      val mockService = mock[VatReturnService]
      when(mockService.createVatReturn(any())) thenReturn Future.successful(AlreadyExists)

      val app =
        new GuiceApplicationBuilder()
          .overrides(bind[VatReturnService].toInstance(mockService))
          .build()

      val vatReturnRequest = arbitrary[VatReturnRequest].sample.value

      running(app) {
        val request =
          FakeRequest(POST, routes.VatReturnController.post().url)
            .withJsonBody(Json.toJson(vatReturnRequest))

        val result = route(app, request).value

        status(result) mustEqual CONFLICT
      }
    }
  }
}
