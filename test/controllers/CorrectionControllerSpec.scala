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
import models.requests.CorrectionRequest
import models.Quarter.Q3
import models.corrections.CorrectionPayload
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
import services.CorrectionService
import uk.gov.hmrc.auth.core.{AuthConnector, MissingBearerToken}

import scala.concurrent.Future

class CorrectionControllerSpec
  extends SpecBase
    with ScalaCheckPropertyChecks
    with Generators {

  ".post" - {

    val correctionRequest = arbitrary[CorrectionRequest].sample.value
    val correctionPayload = arbitrary[CorrectionPayload].sample.value

    lazy val request =
      FakeRequest(POST, routes.CorrectionController.post().url)
        .withJsonBody(Json.toJson(correctionRequest))

    "must save a VAT return and respond with Created" in {
      val mockService = mock[CorrectionService]

      when(mockService.createCorrection(any()))
        .thenReturn(Future.successful(Some(correctionPayload)))

      val app =
        applicationBuilder
          .overrides(bind[CorrectionService].toInstance(mockService))
          .build()

      running(app) {

        val result = route(app, request).value

        status(result) mustEqual CREATED
        contentAsJson(result) mustBe Json.toJson(correctionPayload)
        verify(mockService, times(1)).createCorrection(eqTo(correctionRequest))
      }
    }

    "must respond with Conflict when trying to save a duplicate" in {

      val mockService = mock[CorrectionService]
      when(mockService.createCorrection(any())).thenReturn(Future.successful(None))

      val app =
        applicationBuilder
          .overrides(bind[CorrectionService].toInstance(mockService))
          .build()

      running(app) {

        val result = route(app, request).value

        status(result) mustEqual CONFLICT
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

    lazy val request = FakeRequest(GET, routes.CorrectionController.list().url)

    "must respond with OK and a sequence of returns when some exist for this user" in {

      val mockService = mock[CorrectionService]
      val corrections =
        Gen
          .nonEmptyListOf(arbitrary[CorrectionPayload])
          .sample.value
          .map(r => r copy (vrn = vrn))

      when(mockService.get(any())) thenReturn Future.successful(corrections)

      val app =
        applicationBuilder
          .overrides(bind[CorrectionService].toInstance(mockService))
          .build()

      running(app) {
        val result = route(app, request).value

        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(corrections)
      }
    }

    "must respond with NOT FOUND when no returns exist for this user" in {

      val mockService = mock[CorrectionService]
      when(mockService.get(any())) thenReturn Future.successful(Seq.empty)

      val app =
        applicationBuilder
          .overrides(bind[CorrectionService].toInstance(mockService))
          .build()

      running(app) {
        val result = route(app, request).value

        status(result) mustEqual NOT_FOUND
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

  ".get(period)" - {
    val period = Period(2021, Q3)

    lazy val request = FakeRequest(GET, routes.CorrectionController.get(period).url)

    "must respond with OK and a sequence of returns when some exist for this user" in {

      val mockService = mock[CorrectionService]
      val correction =
        Gen
          .nonEmptyListOf(arbitrary[CorrectionPayload])
          .sample.value
          .map(r => r copy (vrn = vrn)).head

      when(mockService.get(any(), any())) thenReturn Future.successful(Some(correction))

      val app =
        applicationBuilder
          .overrides(bind[CorrectionService].toInstance(mockService))
          .build()

      running(app) {
        val result = route(app, request).value

        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(correction)
      }
    }

    "must respond with NOT FOUND when specified return doesn't exist" in {

      val mockService = mock[CorrectionService]
      when(mockService.get(any(), any())) thenReturn Future.successful(None)

      val app =
        applicationBuilder
          .overrides(bind[CorrectionService].toInstance(mockService))
          .build()

      running(app) {
        val result = route(app, request).value

        status(result) mustEqual NOT_FOUND
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
}
