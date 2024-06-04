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
import models.Quarter.{Q1, Q2, Q3, Q4}
import models.SubmissionStatus.{Due, Next, Overdue}
import models._
import models.exclusions.{ExcludedTrader, ExclusionReason}
import models.yourAccount._
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
  private val exclusionReason = Gen.oneOf(ExclusionReason.values).sample.value
  private val excludedTrader: ExcludedTrader = ExcludedTrader(
    RegistrationData.registration.vrn,
    exclusionReason,
    period.firstDay
  )

  ".listStatus(commencementDate)" - {

    val mockRegistrationConnector = mock[RegistrationConnector]
    val commencementDate = LocalDate.now()

    lazy val request = FakeRequest(GET, routes.ReturnStatusController.listStatuses(commencementDate).url)

    "must respond with OK and a sequence of periods with statuses" in {

      when(mockRegistrationConnector.getRegistration(any())(any())) thenReturn
        Future.successful(Some(RegistrationData.registration))

      val mockVatReturnService = mock[VatReturnService]
      val mockPeriodService = mock[PeriodService]

      val nextPeriod = StandardPeriod(2021, Q4)
      val vatReturn =
        Gen
          .nonEmptyListOf(arbitrary[VatReturn])
          .sample.value
          .map(r => r.copy(vrn = vrn, reference = ReturnReference(vrn, r.period))).head

      when(mockVatReturnService.get(any())) thenReturn Future.successful(Seq(vatReturn.copy(period = period)))
      when(mockPeriodService.getReturnPeriods(any())) thenReturn Seq(period)
      when(mockPeriodService.getAllPeriods(any())) thenReturn Seq(period)
      when(mockPeriodService.getNextPeriod(any())) thenReturn nextPeriod

      val app =
        applicationBuilder
          .overrides(bind[VatReturnService].toInstance(mockVatReturnService))
          .overrides(bind[PeriodService].toInstance(mockPeriodService))
          .overrides(bind[RegistrationConnector].toInstance(mockRegistrationConnector))
          .build()

      running(app) {
        val result = route(app, request).value

        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(Seq(
          PeriodWithStatus(period, SubmissionStatus.Complete),
          PeriodWithStatus(nextPeriod, SubmissionStatus.Next))
        )
      }
    }

    "must respond with OK and a sequence of periods with statuses and trader is excluded" in {

      when(mockRegistrationConnector.getRegistration(any())(any())) thenReturn
        Future.successful(Some(RegistrationData.registration.copy(excludedTrader = Some(excludedTrader))))

      val mockVatReturnService = mock[VatReturnService]
      val mockPeriodService = mock[PeriodService]

      val period2021Q4 = StandardPeriod(2021, Q4)
      val period2022Q1 = StandardPeriod(2022, Q1)
      val nextPeriod = period2021Q4
      val vatReturn =
        Gen
          .nonEmptyListOf(arbitrary[VatReturn])
          .sample.value
          .map(r => r.copy(vrn = vrn, reference = ReturnReference(vrn, r.period))).head

      when(mockVatReturnService.get(any())) thenReturn Future.successful(Seq(vatReturn.copy(period = period)))
      when(mockPeriodService.getReturnPeriods(any())) thenReturn Seq(period, period2021Q4, period2022Q1)
      when(mockPeriodService.getAllPeriods(any())) thenReturn Seq(period, period2021Q4, period2022Q1)
      when(mockPeriodService.getNextPeriod(any())) thenReturn nextPeriod

      val app =
        applicationBuilder
          .overrides(bind[VatReturnService].toInstance(mockVatReturnService))
          .overrides(bind[PeriodService].toInstance(mockPeriodService))
          .overrides(bind[RegistrationConnector].toInstance(mockRegistrationConnector))

          .build()

      running(app) {
        val result = route(app, request).value

        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(Seq(
          PeriodWithStatus(period, SubmissionStatus.Complete),
          PeriodWithStatus(period2021Q4, SubmissionStatus.Excluded),
          PeriodWithStatus(period2022Q1, SubmissionStatus.Excluded))
        )
      }
    }
  }

  ".getCurrentReturns()" - {
    val stubClock: Clock = Clock.fixed(LocalDate.of(2022, 10, 1).atStartOfDay(ZoneId.systemDefault).toInstant, ZoneId.systemDefault)
    val period = StandardPeriod(2021, Q2)
    val period0 = StandardPeriod(2021, Q3)
    val period1 = StandardPeriod(2022, Q1)
    val period2 = StandardPeriod(2022, Q2)
    val period3 = StandardPeriod(2022, Q3)
    val periods = Seq(period, period0, period1, period2, period3)

    lazy val request = FakeRequest(GET, routes.ReturnStatusController.getCurrentReturns(vrn.vrn).url)
    "must respond with OK and the OpenReturns model" - {

      "with no returns in progress, due or overdue if there are no returns due yet" in {

        val mockVatReturnService = mock[VatReturnService]
        val mockPeriodService = mock[PeriodService]
        val mockS4LaterRepository = mock[SaveForLaterRepository]
        val mockRegConnector = mock[RegistrationConnector]

        when(mockVatReturnService.get(any())) thenReturn Future.successful(Seq.empty)
        when(mockPeriodService.getReturnPeriods(any())) thenReturn Seq.empty
        when(mockPeriodService.getAllPeriods(any())) thenReturn Seq(period)
        when(mockPeriodService.getNextPeriod(any())) thenReturn period
        when(mockS4LaterRepository.get(any())) thenReturn Future.successful(Seq.empty)
        when(mockRegConnector.getRegistration(any())(any())) thenReturn Future.successful(Some(RegistrationData.registration))
        when(mockPeriodService.getRunningPeriod(any())) thenReturn period

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
          contentAsJson(result) mustEqual Json.toJson(CurrentReturns(Seq(Return.fromPeriod(period, Next, false, true)), false, false))
        }
      }

      "with no returns in progress, due or overdue if there are no returns due yet and commencement date is in the future" in {

        val mockVatReturnService = mock[VatReturnService]
        val mockPeriodService = mock[PeriodService]
        val mockS4LaterRepository = mock[SaveForLaterRepository]
        val mockRegConnector = mock[RegistrationConnector]

        when(mockVatReturnService.get(any())) thenReturn Future.successful(Seq.empty)
        when(mockPeriodService.getReturnPeriods(any())) thenReturn Seq.empty
        when(mockPeriodService.getAllPeriods(any())) thenReturn Seq(period)
        when(mockPeriodService.getNextPeriod(any())) thenReturn period0
        when(mockS4LaterRepository.get(any())) thenReturn Future.successful(Seq.empty)
        when(mockRegConnector.getRegistration(any())(any())) thenReturn Future.successful(Some(RegistrationData.registration))
        when(mockPeriodService.getRunningPeriod(any())) thenReturn period0

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
          contentAsJson(result) mustEqual Json.toJson(CurrentReturns(Seq(Return.fromPeriod(period0, Next, false, true)), false, false))
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
            .map(r => r.copy(vrn = vrn, reference = ReturnReference(vrn, r.period))).head

        when(mockVatReturnService.get(any())) thenReturn Future.successful(Seq(vatReturn.copy(period = period)))
        when(mockPeriodService.getReturnPeriods(any())) thenReturn Seq(period)
        when(mockPeriodService.getAllPeriods(any())) thenReturn Seq(period)
        when(mockPeriodService.getNextPeriod(any())) thenReturn period
        when(mockS4LaterRepository.get(any())) thenReturn Future.successful(Seq.empty)
        when(mockRegConnector.getRegistration(any())(any())) thenReturn Future.successful(Some(RegistrationData.registration))
        when(mockPeriodService.getRunningPeriod(any())) thenReturn period

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
          contentAsJson(result) mustEqual Json.toJson(CurrentReturns(Seq(Return.fromPeriod(period, Next, false, true)), false, false))
        }
      }

      "with a return due but not in progress if there's one return due but no saved answers" in {
        val period = StandardPeriod(2022, Q3)
        val mockVatReturnService = mock[VatReturnService]
        val mockPeriodService = mock[PeriodService]
        val mockS4LaterRepository = mock[SaveForLaterRepository]
        val mockRegConnector = mock[RegistrationConnector]

        when(mockVatReturnService.get(any())) thenReturn Future.successful(Seq.empty)
        when(mockPeriodService.getReturnPeriods(any())) thenReturn Seq(period)
        when(mockS4LaterRepository.get(any())) thenReturn Future.successful(Seq.empty)
        when(mockRegConnector.getRegistration(any())(any())) thenReturn Future.successful(Some(RegistrationData.registration))
        when(mockPeriodService.getNextPeriod(any())) thenReturn period
        when(mockPeriodService.getAllPeriods(any())) thenReturn Seq(period)
        when(mockPeriodService.getRunningPeriod(any())) thenReturn period

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
          contentAsJson(result) mustEqual Json.toJson(CurrentReturns(Seq(Return.fromPeriod(period, Due, false, true)), false, false))
        }
      }

      "with some overdue returns but nothing in progress" in {

        val mockVatReturnService = mock[VatReturnService]
        val mockPeriodService = mock[PeriodService]
        val mockS4LaterRepository = mock[SaveForLaterRepository]
        val mockRegConnector = mock[RegistrationConnector]
        val periods = Seq(period, period0, period2)
        val returns = Seq(
          Return.fromPeriod(period, Overdue, false, true),
          Return.fromPeriod(period0, Overdue, false, false),
          Return.fromPeriod(period2, Overdue, false, false))
        when(mockVatReturnService.get(any())) thenReturn Future.successful(Seq.empty)
        when(mockPeriodService.getReturnPeriods(any())) thenReturn periods
        when(mockS4LaterRepository.get(any())) thenReturn Future.successful(Seq.empty)
        when(mockRegConnector.getRegistration(any())(any())) thenReturn Future.successful(Some(RegistrationData.registration))
        when(mockPeriodService.getNextPeriod(any())) thenReturn period
        when(mockPeriodService.getAllPeriods(any())) thenReturn Seq(period)
        when(mockPeriodService.getRunningPeriod(any())) thenReturn period

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
          contentAsJson(result) mustEqual Json.toJson(CurrentReturns(returns, false, false))
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
        when(mockRegConnector.getRegistration(any())(any())) thenReturn Future.successful(Some(RegistrationData.registration))
        when(mockPeriodService.getNextPeriod(any())) thenReturn period
        when(mockPeriodService.getAllPeriods(any())) thenReturn Seq(period)
        when(mockPeriodService.getRunningPeriod(any())) thenReturn period
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
          contentAsJson(result) mustEqual Json.toJson(
            CurrentReturns(Seq(
              Return.fromPeriod(period, Overdue, inProgress = false, isOldest = true),
              Return.fromPeriod(period0, Overdue, inProgress = false, isOldest = false),
              Return.fromPeriod(period1, Overdue, inProgress = false, isOldest = false),
              Return.fromPeriod(period2, Overdue, inProgress = false, isOldest = false),
              Return.fromPeriod(period3, Due, inProgress = false, isOldest = false)
            ),
            excluded = false,
            finalReturnsCompleted = false
          ))
        }
      }

      "with a return due and in progress if there's one return due and saved answers" in {
        val period = StandardPeriod(2022, Q3)
        val mockVatReturnService = mock[VatReturnService]
        val mockPeriodService = mock[PeriodService]
        val mockS4LaterRepository = mock[SaveForLaterRepository]
        val mockRegConnector = mock[RegistrationConnector]
        val answers = arbitrary[SavedUserAnswers].sample.value.copy(period = period)

        when(mockVatReturnService.get(any())) thenReturn Future.successful(Seq.empty)
        when(mockPeriodService.getReturnPeriods(any())) thenReturn Seq(period)
        when(mockPeriodService.getNextPeriod(any())) thenReturn period
        when(mockS4LaterRepository.get(any())) thenReturn Future.successful(Seq(answers))
        when(mockRegConnector.getRegistration(any())(any())) thenReturn Future.successful(Some(RegistrationData.registration))
        when(mockPeriodService.getAllPeriods(any())) thenReturn Seq(period)
        when(mockPeriodService.getRunningPeriod(any())) thenReturn period

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
          contentAsJson(result) mustEqual Json.toJson(CurrentReturns(
            Seq(Return.fromPeriod(period, Due, true, true)
          ),
            excluded = false,
            finalReturnsCompleted = false
          ))
        }
      }

      "with an excluded trader's final return due but not in progress and no saved answers" in {
        val periodQ2 = StandardPeriod(2022, Q2)
        val periodQ3 = StandardPeriod(2022, Q3)
        val mockVatReturnService = mock[VatReturnService]
        val mockPeriodService = mock[PeriodService]
        val mockS4LaterRepository = mock[SaveForLaterRepository]
        val mockRegConnector = mock[RegistrationConnector]

        when(mockVatReturnService.get(any())) thenReturn Future.successful(Seq.empty)
        when(mockVatReturnService.get(any(), any())) thenReturn Future.successful(None)
        when(mockPeriodService.getReturnPeriods(any())) thenReturn Seq(periodQ2, periodQ3)
        when(mockPeriodService.getNextPeriod(any())) thenReturn periodQ3
        when(mockS4LaterRepository.get(any())) thenReturn Future.successful(Seq.empty)
        when(mockRegConnector.getRegistration(any())(any())) thenReturn
          Future.successful(Some(RegistrationData.registration.copy(excludedTrader = Some(excludedTrader.copy(effectiveDate = periodQ2.firstDay)))))

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
          contentAsJson(result) mustEqual Json.toJson(CurrentReturns(
            Seq(
              Return.fromPeriod(periodQ2, Overdue, false, true)
            ),
            excluded = true,
            finalReturnsCompleted = false
          ))
        }
      }

      "with an excluded trader's final return completed and can't start any more returns" in {
        val periodQ2 = StandardPeriod(2022, Q2)
        val periodQ3 = StandardPeriod(2022, Q3)
        val mockVatReturnService = mock[VatReturnService]
        val mockPeriodService = mock[PeriodService]
        val mockS4LaterRepository = mock[SaveForLaterRepository]
        val mockRegConnector = mock[RegistrationConnector]


        when(mockVatReturnService.get(any())) thenReturn Future.successful(Seq(completeVatReturn.copy(period = periodQ2)))
        when(mockVatReturnService.get(any(), any())) thenReturn Future.successful(Some(completeVatReturn.copy(period = periodQ2)))
        when(mockPeriodService.getReturnPeriods(any())) thenReturn Seq(periodQ2, periodQ3)
        when(mockPeriodService.getNextPeriod(any())) thenReturn periodQ3
        when(mockS4LaterRepository.get(any())) thenReturn Future.successful(Seq.empty)
        when(mockRegConnector.getRegistration(any())(any())) thenReturn
          Future.successful(Some(RegistrationData.registration.copy(excludedTrader = Some(excludedTrader.copy(effectiveDate = periodQ2.firstDay)))))

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
          contentAsJson(result) mustEqual Json.toJson(CurrentReturns(
            Seq.empty,
            excluded = true,
            finalReturnsCompleted = true
          ))
        }
      }

    }

    "must return Not Found when no registration is found for VRN" in {

      val mockRegConnector = mock[RegistrationConnector]

      when(mockRegConnector.getRegistration(any())(any())) thenReturn Future.successful(None)

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
