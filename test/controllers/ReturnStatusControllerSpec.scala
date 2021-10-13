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
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.inject.bind
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{PeriodService, VatReturnService}

import java.time.LocalDate
import scala.concurrent.Future
import scala.language.implicitConversions

class ReturnStatusControllerSpec
  extends SpecBase
    with ScalaCheckPropertyChecks
    with Generators {

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
}
