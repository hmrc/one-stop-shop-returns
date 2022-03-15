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
import controllers.actions.FakeFailingAuthConnector
import generators.Generators
import models._
import models.yourAccount._
import models.Quarter.{Q1, Q2, Q3}
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
import repositories.SaveForLaterRepository
import services.{PeriodService, VatReturnService}
import testutils.RegistrationData
import uk.gov.hmrc.auth.core.{AuthConnector, MissingBearerToken}
import uk.gov.hmrc.domain.Vrn

import java.time.{Clock, LocalDate, ZoneId}
import scala.concurrent.Future

class ReturnStatusControllerSpec
  extends SpecBase
    with ScalaCheckPropertyChecks
    with Generators {

  private val authorisedVrn = Vrn("123456789")
  private val notAuthorisedVrn = arbitraryVrn.arbitrary.retryUntil(_ != authorisedVrn).sample.value

  ".listStatus(commencementDate)" - {
    val period = Period(2021, Q3)
    val commencementDate = LocalDate.now()

    lazy val request = FakeRequest(GET, routes.ReturnStatusController.listStatuses(commencementDate).url)

    "must respond with OK and a sequence of periods with statuses" in {

      val mockVatReturnService = mock[VatReturnService]
      val mockPeriodService = mock[PeriodService]
      val vatReturn =
        Gen
          .nonEmptyListOf(arbitrary[VatReturn])
          .sample.value
          .map(r => r copy(vrn = vrn, reference = ReturnReference(vrn, r.period))).head

      when(mockVatReturnService.get(any())) thenReturn Future.successful(Seq(vatReturn.copy(period = period)))
      when(mockPeriodService.getReturnPeriods(any())) thenReturn Seq(period)

      val app =
        applicationBuilder
          .overrides(bind[VatReturnService].toInstance(mockVatReturnService))
          .overrides(bind[PeriodService].toInstance(mockPeriodService))
          .build()

      running(app) {
        val result = route(app, request).value

        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(Seq(PeriodWithStatus(period, SubmissionStatus.Complete)))
      }
    }

  }

  ".getCurrentReturns()" - {
    val stubClock: Clock = Clock.fixed(LocalDate.of(2022, 10, 1).atStartOfDay(ZoneId.systemDefault).toInstant, ZoneId.systemDefault)
    val period = Period(2021, Q2)
    val period0 = Period(2021, Q3)
    val period1 = Period(2022, Q1)
    val period2 = Period(2022, Q2)
    val period3 = Period(2022, Q3)
    val periods = Seq(period, period0, period1, period2, period3)
    val commencementDate = LocalDate.of(2021, 1, 1)

    lazy val request = FakeRequest(GET, routes.ReturnStatusController.getCurrentReturns(vrn.vrn).url)
    "must respond with OK and the OpenReturns model" - {

      "with no returns in progress, due or overdue if there are no returns due yet" in {

        val mockVatReturnService = mock[VatReturnService]
        val mockPeriodService = mock[PeriodService]
        val mockS4LaterRepository = mock[SaveForLaterRepository]
        val mockRegConnector = mock[RegistrationConnector]

        when(mockVatReturnService.get(any())) thenReturn Future.successful(Seq.empty)
        when(mockPeriodService.getReturnPeriods(any())) thenReturn Seq.empty
        when(mockS4LaterRepository.get(any())) thenReturn Future.successful(Seq.empty)
        when(mockRegConnector.getRegistration()(any())) thenReturn Future.successful(Some(RegistrationData.registration))

        val app =
          applicationBuilder
            .overrides(bind[VatReturnService].toInstance(mockVatReturnService))
            .overrides(bind[PeriodService].toInstance(mockPeriodService))
            .overrides(bind[SaveForLaterRepository].toInstance(mockS4LaterRepository))
            .overrides(bind[RegistrationConnector].toInstance(mockRegConnector))
            .overrides(bind[Clock].toInstance(stubClock))
            .build()

        running(app) {
          val result = route(app, request).value

          status(result) mustEqual OK
          contentAsJson(result) mustEqual Json.toJson(OpenReturns(None, None, Seq.empty))
        }
      }

      "with no returns in progress, due or overdue if all returns are complete" in {

        val mockVatReturnService = mock[VatReturnService]
        val mockPeriodService = mock[PeriodService]
        val mockS4LaterRepository = mock[SaveForLaterRepository]
        val mockRegConnector = mock[RegistrationConnector]

        val vatReturn =
          Gen
            .nonEmptyListOf(arbitrary[VatReturn])
            .sample.value
            .map(r => r copy(vrn = vrn, reference = ReturnReference(vrn, r.period))).head

        when(mockVatReturnService.get(any())) thenReturn Future.successful(Seq(vatReturn.copy(period = period)))
        when(mockPeriodService.getReturnPeriods(any())) thenReturn Seq(period)
        when(mockS4LaterRepository.get(any())) thenReturn Future.successful(Seq.empty)
        when(mockRegConnector.getRegistration()(any())) thenReturn Future.successful(Some(RegistrationData.registration))

        val app =
          applicationBuilder
            .overrides(bind[VatReturnService].toInstance(mockVatReturnService))
            .overrides(bind[PeriodService].toInstance(mockPeriodService))
            .overrides(bind[SaveForLaterRepository].toInstance(mockS4LaterRepository))
            .overrides(bind[RegistrationConnector].toInstance(mockRegConnector))
            .overrides(bind[Clock].toInstance(stubClock))
            .build()

        running(app) {
          val result = route(app, request).value

          status(result) mustEqual OK
          contentAsJson(result) mustEqual Json.toJson(OpenReturns(None, None, Seq.empty))
        }
      }

      "with a return due but not in progress if there's one return due but no saved answers" in {
        val period = Period(2022, Q3)
        val mockVatReturnService = mock[VatReturnService]
        val mockPeriodService = mock[PeriodService]
        val mockS4LaterRepository = mock[SaveForLaterRepository]
        val mockRegConnector = mock[RegistrationConnector]

        when(mockVatReturnService.get(any())) thenReturn Future.successful(Seq.empty)
        when(mockPeriodService.getReturnPeriods(any())) thenReturn Seq(period)
        when(mockS4LaterRepository.get(any())) thenReturn Future.successful(Seq.empty)
        when(mockRegConnector.getRegistration()(any())) thenReturn Future.successful(Some(RegistrationData.registration))

        val app =
          applicationBuilder
            .overrides(bind[VatReturnService].toInstance(mockVatReturnService))
            .overrides(bind[PeriodService].toInstance(mockPeriodService))
            .overrides(bind[SaveForLaterRepository].toInstance(mockS4LaterRepository))
            .overrides(bind[RegistrationConnector].toInstance(mockRegConnector))
            .overrides(bind[Clock].toInstance(stubClock))
            .build()

        running(app) {
          val result = route(app, request).value

          status(result) mustEqual OK
          contentAsJson(result) mustEqual Json.toJson(OpenReturns(None, Some(Return(period, period.firstDay, period.lastDay, period.paymentDeadline)), Seq.empty))
        }
      }

      "with some overdue returns but nothing in progress" in {

        val mockVatReturnService = mock[VatReturnService]
        val mockPeriodService = mock[PeriodService]
        val mockS4LaterRepository = mock[SaveForLaterRepository]
        val mockRegConnector = mock[RegistrationConnector]
        val periods = Seq(period, period0, period2)
        val returns = Seq(
          Return.fromPeriod(period),
          Return.fromPeriod(period0),
          Return.fromPeriod(period2))
        when(mockVatReturnService.get(any())) thenReturn Future.successful(Seq.empty)
        when(mockPeriodService.getReturnPeriods(any())) thenReturn periods
        when(mockS4LaterRepository.get(any())) thenReturn Future.successful(Seq.empty)
        when(mockRegConnector.getRegistration()(any())) thenReturn Future.successful(Some(RegistrationData.registration))

        val app =
          applicationBuilder
            .overrides(bind[VatReturnService].toInstance(mockVatReturnService))
            .overrides(bind[PeriodService].toInstance(mockPeriodService))
            .overrides(bind[SaveForLaterRepository].toInstance(mockS4LaterRepository))
            .overrides(bind[RegistrationConnector].toInstance(mockRegConnector))
            .overrides(bind[Clock].toInstance(stubClock))
            .build()

        running(app) {
          val result = route(app, request).value

          status(result) mustEqual OK
          contentAsJson(result) mustEqual Json.toJson(OpenReturns(None, None, returns))
        }
      }

      "with a return due and some returns overdue and nothing in progress" in {

        val mockVatReturnService = mock[VatReturnService]
        val mockPeriodService = mock[PeriodService]
        val mockS4LaterRepository = mock[SaveForLaterRepository]
        val mockRegConnector = mock[RegistrationConnector]

        when(mockVatReturnService.get(any())) thenReturn Future.successful(Seq.empty)
        when(mockPeriodService.getReturnPeriods(any())) thenReturn periods
        when(mockS4LaterRepository.get(any())) thenReturn Future.successful(Seq.empty)
        when(mockRegConnector.getRegistration()(any())) thenReturn Future.successful(Some(RegistrationData.registration))
        val app =
          applicationBuilder
            .overrides(bind[VatReturnService].toInstance(mockVatReturnService))
            .overrides(bind[PeriodService].toInstance(mockPeriodService))
            .overrides(bind[SaveForLaterRepository].toInstance(mockS4LaterRepository))
            .overrides(bind[RegistrationConnector].toInstance(mockRegConnector))
            .overrides(bind[Clock].toInstance(stubClock))
            .build()

        running(app) {
          val result = route(app, request).value

          status(result) mustEqual OK
          contentAsJson(result) mustEqual Json.toJson(OpenReturns(None, Some(Return.fromPeriod(period3)),
            Seq(Return.fromPeriod(period), Return.fromPeriod(period0), Return.fromPeriod(period1), Return.fromPeriod(period2))
          ))
        }
      }

      "with a return due and in progress if there's one return due and saved answers" in {
        val period = Period(2022, Q3)
        val mockVatReturnService = mock[VatReturnService]
        val mockPeriodService = mock[PeriodService]
        val mockS4LaterRepository = mock[SaveForLaterRepository]
        val mockRegConnector = mock[RegistrationConnector]
        val answers = arbitrary[SavedUserAnswers].sample.value.copy(period = period)

        when(mockVatReturnService.get(any())) thenReturn Future.successful(Seq.empty)
        when(mockPeriodService.getReturnPeriods(any())) thenReturn Seq(period)
        when(mockS4LaterRepository.get(any())) thenReturn Future.successful(Seq(answers))
        when(mockRegConnector.getRegistration()(any())) thenReturn Future.successful(Some(RegistrationData.registration))

        val app =
          applicationBuilder
            .overrides(bind[VatReturnService].toInstance(mockVatReturnService))
            .overrides(bind[PeriodService].toInstance(mockPeriodService))
            .overrides(bind[SaveForLaterRepository].toInstance(mockS4LaterRepository))
            .overrides(bind[RegistrationConnector].toInstance(mockRegConnector))
            .overrides(bind[Clock].toInstance(stubClock))
            .build()

        running(app) {
          val result = route(app, request).value

          status(result) mustEqual OK
          contentAsJson(result) mustEqual Json.toJson(OpenReturns(Some(Return.fromPeriod(period)), Some(Return.fromPeriod(period)), Seq.empty))
        }
      }
    }

    "must return Not Found when no registration is found for VRN" in {

      val mockRegConnector = mock[RegistrationConnector]

      when(mockRegConnector.getRegistration()(any())) thenReturn Future.successful(None)

      val app =
        applicationBuilder
          .overrides(bind[RegistrationConnector].toInstance(mockRegConnector))
          .overrides(bind[Clock].toInstance(stubClock))
          .build()

      running(app) {
        val result = route(app, request).value

        status(result) mustEqual NOT_FOUND
      }
    }

    "must return Unauthorised if VRNs in the URI and in the request do not match" in {

      val app =
        applicationBuilder
          .build()

      running(app) {
        val result = route(app, FakeRequest(GET, routes.FinancialDataController.prepareFinancialData(notAuthorisedVrn.vrn).url)).value

        status(result) mustEqual UNAUTHORIZED
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
