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
import models.requests.SaveForLaterRequest
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{times, verify, when}
import org.scalacheck.Arbitrary.arbitrary
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.SaveForLaterService
import uk.gov.hmrc.auth.core.{AuthConnector, MissingBearerToken}

import scala.concurrent.Future

class SaveForLaterControllerSpec
  extends SpecBase
    with ScalaCheckPropertyChecks
    with Generators {

  ".post" - {

    val s4lRequest = arbitrary[SaveForLaterRequest].sample.value
    val savedAnswers        = arbitrary[SavedUserAnswers].sample.value

    lazy val request =
      FakeRequest(POST, routes.SaveForLaterController.post().url)
        .withJsonBody(Json.toJson(s4lRequest))

    "must save a VAT return and respond with Created" in {
      val mockService = mock[SaveForLaterService]

      when(mockService.saveAnswers(any()))
        .thenReturn(Future.successful(savedAnswers))

      val app =
        applicationBuilder
          .overrides(bind[SaveForLaterService].toInstance(mockService))
          .build()

      running(app) {

        val result = route(app, request).value

        status(result) mustEqual CREATED
        contentAsJson(result) mustBe Json.toJson(savedAnswers)
        verify(mockService, times(1)).saveAnswers(eqTo(s4lRequest))
      }
    }

    "must respond with Unauthorized when the user is not authorised" in {

      val app =
        new GuiceApplicationBuilder()
          .overrides(bind[AuthConnector].toInstance(new FakeFailingAuthConnector(new MissingBearerToken)))
          .build()

      running(app) {

        val result = route(app, request).value
        status(result) mustEqual UNAUTHORIZED
      }
    }
  }

  ".get" - {
    val savedAnswers        = arbitrary[SavedUserAnswers].sample.value
    lazy val request =
      FakeRequest(GET, routes.SaveForLaterController.get().url)

    "must return OK and a response when Saved User Answers are found for the vrn and period" in {
      val mockService = mock[SaveForLaterService]

      when(mockService.get(any()))
        .thenReturn(Future.successful(Seq(savedAnswers)))

      val app =
        applicationBuilder
          .overrides(bind[SaveForLaterService].toInstance(mockService))
          .build()

      running(app) {

        val result = route(app, request).value

        status(result) mustEqual OK
        contentAsJson(result) mustBe Json.toJson(savedAnswers)
        verify(mockService, times(1)).get(any())
      }
    }

    "must return NOT_FOUND when no answers are found" in {
      val mockService = mock[SaveForLaterService]

      when(mockService.get(any()))
        .thenReturn(Future.successful(Seq()))

      val app =
        applicationBuilder
          .overrides(bind[SaveForLaterService].toInstance(mockService))
          .build()

      running(app) {

        val result = route(app, request).value

        status(result) mustEqual NOT_FOUND
        verify(mockService, times(1)).get(any())
      }
    }
  }

  ".delete" - {
    val period = arbitrary[Period].sample.value
    lazy val request =
      FakeRequest(GET, routes.SaveForLaterController.delete(period).url)

    "must return OK" in {
      val mockService = mock[SaveForLaterService]

      when(mockService.delete(any(), any()))
        .thenReturn(Future.successful(true))

      val app =
        applicationBuilder
          .overrides(bind[SaveForLaterService].toInstance(mockService))
          .build()

      running(app) {

        val result = route(app, request).value

        status(result) mustEqual OK
        contentAsJson(result) mustBe Json.toJson(true)
        verify(mockService, times(1)).delete(any(), any())
      }
    }
  }

}
