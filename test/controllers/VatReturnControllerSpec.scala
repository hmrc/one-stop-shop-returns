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
import connectors.{RegistrationConnector, VatReturnConnector}
import controllers.actions.FakeFailingAuthConnector
import generators.Generators
import models.*
import models.Quarter.Q3
import models.core.CoreErrorResponse.REGISTRATION_NOT_FOUND
import models.core.{CoreErrorResponse, EisErrorResponse}
import models.corrections.CorrectionPayload
import models.etmp.{EtmpObligations, EtmpVatReturn}
import models.requests.{VatReturnRequest, VatReturnWithCorrectionRequest}
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito
import org.mockito.Mockito.{times, verify, when}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import services.VatReturnService
import testutils.RegistrationData
import uk.gov.hmrc.auth.core.{AuthConnector, MissingBearerToken}
import uk.gov.hmrc.domain.Vrn
import utils.FutureSyntax.FutureOps

import java.time.Instant
import scala.concurrent.Future

class VatReturnControllerSpec
  extends SpecBase
    with ScalaCheckPropertyChecks
    with Generators
    with BeforeAndAfterEach {

  private val mockVatReturnConnector: VatReturnConnector = mock[VatReturnConnector]

  override def beforeEach(): Unit = {
    Mockito.reset(mockVatReturnConnector)
  }
  
  ".post" - {

    val vatReturnRequest = arbitrary[VatReturnRequest].sample.value
    val vatReturn = arbitrary[VatReturn].sample.value

    lazy val request =
      FakeRequest(POST, routes.VatReturnController.post().url)
        .withJsonBody(Json.toJson(vatReturnRequest))

    "must save a VAT return and respond with Created" in {
      val mockService = mock[VatReturnService]

      when(mockService.createVatReturn(any())(any(), any())) `thenReturn` Future.successful(Right(Some(vatReturn)))

      val app =
        applicationBuilder
          .overrides(bind[VatReturnService].toInstance(mockService))
          .build()

      running(app) {

        val result = route(app, request).value

        status(result) mustEqual CREATED
        contentAsJson(result) mustBe Json.toJson(vatReturn)
        verify(mockService, times(1)).createVatReturn(eqTo(vatReturnRequest))(any(), any())
      }
    }

    "must respond with Conflict when trying to save a duplicate" in {

      val mockService = mock[VatReturnService]
      when(mockService.createVatReturn(any())(any(), any())).thenReturn(Future.successful(Right(None)))

      val app =
        applicationBuilder
          .overrides(bind[VatReturnService].toInstance(mockService))
          .build()

      running(app) {

        val result = route(app, request).value

        status(result) mustEqual CONFLICT
      }
    }

    "must respond with NotFound when registration is not in core" in {
      val coreErrorResponse = CoreErrorResponse(Instant.now(), None, REGISTRATION_NOT_FOUND, "There was an error")
      val eisErrorResponse = EisErrorResponse(coreErrorResponse)

      val mockService = mock[VatReturnService]
      when(mockService.createVatReturn(any())(any(), any())).thenReturn(Future.successful(Left(eisErrorResponse)))

      val app =
        applicationBuilder
          .overrides(bind[VatReturnService].toInstance(mockService))
          .build()

      running(app) {

        val result = route(app, request).value

        status(result) mustEqual NOT_FOUND
      }
    }

    "must respond with ServiceUnavailable(coreError) when error received from core" in {
      val coreErrorResponse = CoreErrorResponse(Instant.now(), None, "OSS_111", "There was an error")
      val eisErrorResponse = EisErrorResponse(coreErrorResponse)

      val mockService = mock[VatReturnService]
      when(mockService.createVatReturn(any())(any(), any())).thenReturn(Future.successful(Left(eisErrorResponse)))

      val app =
        applicationBuilder
          .overrides(bind[VatReturnService].toInstance(mockService))
          .build()

      running(app) {

        val result = route(app, request).value

        status(result) mustEqual SERVICE_UNAVAILABLE
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

  ".postWithCorrection" - {

    val vatReturnWithCorrectionRequest = arbitrary[VatReturnWithCorrectionRequest].sample.value
    val vatReturn = arbitrary[VatReturn].sample.value
    val correctionPayload = arbitrary[CorrectionPayload].sample.value

    lazy val request =
      FakeRequest(POST, routes.VatReturnController.postWithCorrection().url)
        .withJsonBody(Json.toJson(vatReturnWithCorrectionRequest))

    "must save a VAT return and respond with Created" in {
      val mockService = mock[VatReturnService]

      when(mockService.createVatReturnWithCorrection(any())(any(), any()))
        .thenReturn(Future.successful(Right(Some((vatReturn, correctionPayload)))))

      val app =
        applicationBuilder
          .overrides(bind[VatReturnService].toInstance(mockService))
          .build()

      running(app) {

        val result = route(app, request).value

        status(result) mustEqual CREATED
        contentAsJson(result) mustBe Json.toJson((vatReturn, correctionPayload))
        verify(mockService, times(1)).createVatReturnWithCorrection(eqTo(vatReturnWithCorrectionRequest))(any(), any())
      }
    }

    "must respond with Conflict when trying to save a duplicate" in {

      val mockService = mock[VatReturnService]
      when(mockService.createVatReturnWithCorrection(any())(any(), any())).thenReturn(Future.successful(Right(None)))

      val app =
        applicationBuilder
          .overrides(bind[VatReturnService].toInstance(mockService))
          .build()

      running(app) {

        val result = route(app, request).value

        status(result) mustEqual CONFLICT
      }
    }

    "must respond with NotFound when registration is not in core" in {
      val coreErrorResponse = CoreErrorResponse(Instant.now(), None, REGISTRATION_NOT_FOUND, "There was an error")
      val eisErrorResponse = EisErrorResponse(coreErrorResponse)

      val mockService = mock[VatReturnService]
      when(mockService.createVatReturnWithCorrection(any())(any(), any())).thenReturn(Future.successful(Left(eisErrorResponse)))

      val app =
        applicationBuilder
          .overrides(bind[VatReturnService].toInstance(mockService))
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

  ".get" - {

    lazy val request = FakeRequest(GET, routes.VatReturnController.list().url)

    "must respond with OK and a sequence of returns when some exist for this user" in {

      val mockService = mock[VatReturnService]
      val returns =
        Gen
          .nonEmptyListOf(arbitrary[VatReturn])
          .sample.value
          .map(r => r.copy(vrn = vrn, reference = ReturnReference(vrn, r.period)))

      when(mockService.get(any())) `thenReturn` Future.successful(returns)

      val app =
        applicationBuilder
          .overrides(bind[VatReturnService].toInstance(mockService))
          .build()

      running(app) {
        val result = route(app, request).value

        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(returns)
      }
    }

    "must respond with NOT FOUND when no returns exist for this user" in {

      val mockService = mock[VatReturnService]
      when(mockService.get(any())) `thenReturn` Future.successful(Seq.empty)

      val app =
        applicationBuilder
          .overrides(bind[VatReturnService].toInstance(mockService))
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

    lazy val request = FakeRequest(GET, routes.VatReturnController.get(period).url)

    "must respond with OK and a sequence of returns when some exist for this user" in {

      val mockService = mock[VatReturnService]
      val vatReturn =
        Gen
          .nonEmptyListOf(arbitrary[VatReturn])
          .sample.value
          .map(r => r.copy(vrn = vrn, reference = ReturnReference(vrn, r.period))).head

      when(mockService.get(any(), any())) `thenReturn` Future.successful(Some(vatReturn))

      val app =
        applicationBuilder
          .overrides(bind[VatReturnService].toInstance(mockService))
          .build()

      running(app) {
        val result = route(app, request).value

        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(vatReturn)
      }
    }

    "must respond with NOT FOUND when specified return doesn't exist" in {

      val mockService = mock[VatReturnService]
      when(mockService.get(any(), any())) `thenReturn` Future.successful(None)

      val app =
        applicationBuilder
          .overrides(bind[VatReturnService].toInstance(mockService))
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

  ".getObligations" - {

    val etmpObligations: EtmpObligations = arbitraryObligations.arbitrary.sample.value
    val vrn = arbitrary[Vrn].sample.value
    val mockCoreVatReturnConnector = mock[VatReturnConnector]
    val mockRegistrationConnector = mock[RegistrationConnector]

    lazy val request = FakeRequest(GET, routes.VatReturnController.getObligations(vrn.vrn).url)

    "must respond with OK and return a valid response" in {

      when(mockCoreVatReturnConnector.getObligations(any(), any())) `thenReturn` Future.successful(Right(etmpObligations))
      when(mockRegistrationConnector.getRegistration(any())(any())) `thenReturn` Future.successful(Some(RegistrationData.registration))

      val app =
        applicationBuilder
          .overrides(bind[VatReturnConnector].toInstance(mockCoreVatReturnConnector))
          .overrides(bind[RegistrationConnector].toInstance(mockRegistrationConnector))
          .build()

      running(app) {
        val result = route(app, request).value

        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(etmpObligations)
      }
    }

    "must respond with and error when the connector responds with an error" in {

      when(mockCoreVatReturnConnector.getObligations(any(), any())) `thenReturn` Future.successful(Left(ServerError))
      when(mockRegistrationConnector.getRegistration(any())(any())) `thenReturn` Future.successful(Some(RegistrationData.registration))

      val app =
        applicationBuilder
          .overrides(bind[VatReturnConnector].toInstance(mockCoreVatReturnConnector))
          .overrides(bind[RegistrationConnector].toInstance(mockRegistrationConnector))
          .build()

      running(app) {
        val result = route(app, request).value

        status(result) mustEqual INTERNAL_SERVER_ERROR
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

  ".getEtmpVatReturn" - {

    lazy val request = FakeRequest(GET, routes.VatReturnController.getEtmpVatReturn(period).url)

    "must return OK with an EtmpVatReturn when it exists" in {

      val etmpVatReturn: EtmpVatReturn = arbitraryEtmpVatReturn.arbitrary.sample.value

      when(mockVatReturnConnector.get(any(), any())) `thenReturn` Right(etmpVatReturn).toFuture

      val app = applicationBuilder
        .overrides(bind[VatReturnConnector].toInstance(mockVatReturnConnector))
        .build()

      running(app) {

        val result = route(app, request).value

        status(result) mustBe OK
        contentAsJson(result) mustBe Json.toJson(etmpVatReturn)
      }
    }

    "must return an error when the server returns an error" in {

      when(mockVatReturnConnector.get(any(), any())) `thenReturn` Left(ServerError).toFuture

      val app = applicationBuilder
        .overrides(bind[VatReturnConnector].toInstance(mockVatReturnConnector))
        .build()

      running(app) {

        val result = route(app, request).value

        status(result) mustBe INTERNAL_SERVER_ERROR
      }
    }

    "must return Unauthorized when the user is not authorised" in {

      val app =
        new GuiceApplicationBuilder()
          .overrides(bind[AuthConnector].toInstance(new FakeFailingAuthConnector(new MissingBearerToken)))
          .build()

      running(app) {

        val result = route(app, request).value

        status(result) mustBe UNAUTHORIZED
      }
    }
  }
}
