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
import models.Quarter.Q3
import models.StandardPeriod
import models.domain.Registration
import models.exclusions.{ExcludedTrader, ExclusionReason}
import models.requests.RegistrationRequest
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalacheck.Gen
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.AnyContent
import services.VatReturnService
import uk.gov.hmrc.domain.Vrn

import scala.concurrent.{ExecutionContext, Future}

class ExclusionServiceSpec extends SpecBase with BeforeAndAfterEach {

  implicit private lazy val ec: ExecutionContext = ExecutionContext.global

  private val mockRegistrationRequest = mock[RegistrationRequest[AnyContent]]
  private val mockRegistration = mock[Registration]
  private val vatReturnService = mock[VatReturnService]
  private val exclusionService = new ExclusionService(vatReturnService)

  private val exclusionReason = Gen.oneOf(ExclusionReason.values).sample.value
  private val exclusionPeriod = StandardPeriod(2022, Q3)

  override def beforeEach(): Unit = {
    Mockito.reset(mockRegistrationRequest)
    super.beforeEach()
  }

  ".hasSubmittedFinalReturn" - {

    "must return true if final return completed" in {
      when(mockRegistrationRequest.registration) thenReturn mockRegistration

      when(mockRegistration.excludedTrader) thenReturn
        Some(ExcludedTrader(Vrn("123456789"), exclusionReason, exclusionPeriod.firstDay))

      when(vatReturnService.get(any(), any())) thenReturn Future.successful(Some(completeVatReturn))

      exclusionService.hasSubmittedFinalReturn()(ec, mockRegistrationRequest).futureValue mustBe true
    }

    "must return false if final return not completed" in {
      when(mockRegistrationRequest.registration) thenReturn mockRegistration

      when(mockRegistration.excludedTrader) thenReturn
        Some(ExcludedTrader(Vrn("123456789"), exclusionReason, exclusionPeriod.firstDay))

      when(vatReturnService.get(any(),any())) thenReturn Future.successful(None)

      exclusionService.hasSubmittedFinalReturn()(ec, mockRegistrationRequest).futureValue mustBe false
    }
  }

}
