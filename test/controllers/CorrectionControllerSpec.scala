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
import connectors.ReturnCorrectionConnector
import controllers.actions.FakeFailingAuthConnector
import generators.Generators
import models._
import models.Quarter.Q3
import models.corrections.{CorrectionPayload, ReturnCorrectionValue}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
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
    val period = StandardPeriod(2021, Q3)

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

  ".getByCorrectionPeriod(period)" - {
    val period = StandardPeriod(2021, Q3)

    lazy val request = FakeRequest(GET, routes.CorrectionController.getByCorrectionPeriod(period).url)

    "must respond with OK and a sequence of returns when some exist for this user" in {

      val mockService = mock[CorrectionService]
      val correction =
        Gen
          .nonEmptyListOf(arbitrary[CorrectionPayload])
          .sample.value
          .map(r => r copy (vrn = vrn)).head

      when(mockService.getByCorrectionPeriod(any(), any())) thenReturn Future.successful(List(correction))

      val app =
        applicationBuilder
          .overrides(bind[CorrectionService].toInstance(mockService))
          .build()

      running(app) {
        val result = route(app, request).value

        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(List(correction))
      }
    }

    "must respond with NOT FOUND when specified return doesn't exist" in {

      val mockService = mock[CorrectionService]
      when(mockService.getByCorrectionPeriod(any(), any())) thenReturn Future.successful(List.empty)

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

  ".getCorrectionValue(countryCode, period)" - {
    val period = StandardPeriod(2021, Q3)
    val country1 = arbitrary[Country].sample.value
    val returnCorrectionValue: ReturnCorrectionValue = arbitraryReturnCorrectionValue.arbitrary.sample.value

    val mockCorrectionService = mock[CorrectionService]
    val mockReturnCorrectionConnector = mock[ReturnCorrectionConnector]

    lazy val request = FakeRequest(GET, routes.CorrectionController.getCorrectionValue(country1.code, period).url)

    "must return OK and a valid response payload when connector returns Right" in {

      when(mockReturnCorrectionConnector.getMaximumCorrectionValue(any(), any(), any())) thenReturn Future.successful(Right(returnCorrectionValue))

      val application = applicationBuilder
        .overrides(bind[CorrectionService].toInstance(mockCorrectionService))
        .overrides(bind[ReturnCorrectionConnector].toInstance(mockReturnCorrectionConnector))
        .build()

      running(application) {
        val result = route(application, request).value

        status(result) mustBe OK
        contentAsJson(result) mustBe Json.toJson(returnCorrectionValue)
      }
    }

    "must return InternalServerError when connector returns an error" in {

      when(mockReturnCorrectionConnector.getMaximumCorrectionValue(any(), any(), any())) thenReturn Future.successful(Left(ServerError))

      val application = applicationBuilder
        .overrides(bind[CorrectionService].toInstance(mockCorrectionService))
        .overrides(bind[ReturnCorrectionConnector].toInstance(mockReturnCorrectionConnector))
        .build()

      running(application) {
        val result = route(application, request).value

        status(result) mustBe INTERNAL_SERVER_ERROR
      }
    }

    "must respond with Unauthorized when the user is not authorised" in {

      val app =
        new GuiceApplicationBuilder()
          .overrides(bind[CorrectionService].toInstance(mockCorrectionService))
          .overrides(bind[ReturnCorrectionConnector].toInstance(mockReturnCorrectionConnector))
          .overrides(bind[AuthConnector].toInstance(new FakeFailingAuthConnector(new MissingBearerToken)))
          .build()

      running(app) {
        val result = route(app, request).value
        status(result) mustEqual UNAUTHORIZED
      }
    }
  }
}
