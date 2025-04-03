/*
 * Copyright 2022 HM Revenue & Customs
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

package services.exclusions

import base.SpecBase
import models.Quarter.{Q2, Q3}
import models.domain.Registration
import models.exclusions.{ExcludedTrader, ExclusionReason}
import models.requests.RegistrationRequest
import models.{PeriodWithStatus, StandardPeriod, SubmissionStatus}
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalacheck.Gen
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.AnyContent
import uk.gov.hmrc.domain.Vrn

class ExclusionServiceSpec extends SpecBase with BeforeAndAfterEach {

  private val mockRegistrationRequest = mock[RegistrationRequest[AnyContent]]
  private val mockRegistration = mock[Registration]
  private val exclusionService = new ExclusionService()

  private val exclusionReason = Gen.oneOf(ExclusionReason.values).sample.value
  private val exclusionPeriod = StandardPeriod(2022, Q3)

  private val periodQ2 = StandardPeriod(2022, Q2)
  private val periodQ3 = StandardPeriod(2022, Q3)
  private val periodsWithStatus = Seq(
    PeriodWithStatus(periodQ2, SubmissionStatus.Complete),
    PeriodWithStatus(periodQ3, SubmissionStatus.Complete)
  )

  private val periodsWithStatusNotSubmitted = Seq(
    PeriodWithStatus(periodQ2, SubmissionStatus.Due),
    PeriodWithStatus(periodQ3, SubmissionStatus.Next)
  )

  override def beforeEach(): Unit = {
    Mockito.reset(mockRegistrationRequest)
    super.beforeEach()
  }

  ".hasSubmittedFinalReturn" - {

    "must return true if final return completed" in {
      when(mockRegistrationRequest.registration) `thenReturn` mockRegistration

      when(mockRegistration.excludedTrader) `thenReturn`
        Some(ExcludedTrader(Vrn("123456789"), exclusionReason, exclusionPeriod.firstDay))

      exclusionService.hasSubmittedFinalReturn(periodsWithStatus)(mockRegistrationRequest) mustBe true
    }

    "must return false if final return not completed" in {
      when(mockRegistrationRequest.registration) `thenReturn` mockRegistration

      when(mockRegistration.excludedTrader) `thenReturn`
        Some(ExcludedTrader(Vrn("123456789"), exclusionReason, exclusionPeriod.firstDay))

      exclusionService.hasSubmittedFinalReturn(Seq.empty)(mockRegistrationRequest) mustBe false
    }

    "must return false if final return not completed as it's due" in {
      when(mockRegistrationRequest.registration) `thenReturn` mockRegistration

      when(mockRegistration.excludedTrader) `thenReturn`
        Some(ExcludedTrader(Vrn("123456789"), exclusionReason, exclusionPeriod.firstDay))

      exclusionService.hasSubmittedFinalReturn(periodsWithStatusNotSubmitted)(mockRegistrationRequest) mustBe false
    }
  }

}
