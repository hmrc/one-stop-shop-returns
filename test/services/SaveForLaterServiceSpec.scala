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

package services

import generators.Generators
import models.{Period, SavedUserAnswers, VatReturn}
import models.corrections.CorrectionPayload
import models.requests.{SaveForLaterRequest, VatReturnRequest, VatReturnWithCorrectionRequest}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import repositories.{SaveForLaterRepository, VatReturnRepository}
import uk.gov.hmrc.domain.Vrn

import java.time.{Clock, Instant, ZoneId}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SaveForLaterServiceSpec
  extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with ScalaCheckPropertyChecks
    with Generators
    with OptionValues
    with ScalaFutures {

  ".saveAnswers" - {

    "must create a SavedUserAnswers, attempt to save it to the repository, and respond with the result of saving" in {

      val now            = Instant.now
      val stubClock      = Clock.fixed(now, ZoneId.systemDefault())
      val answers      = arbitrary[SavedUserAnswers].sample.value
      val insertResult   = answers
      val mockRepository = mock[SaveForLaterRepository]

      when(mockRepository.set(any())) thenReturn Future.successful(insertResult)

      val request = arbitrary[SaveForLaterRequest].sample.value
      val service = new SaveForLaterService(mockRepository, stubClock)

      val result = service.saveAnswers(request).futureValue

      result mustEqual insertResult
      verify(mockRepository, times(1)).set(any())
    }
  }

  ".get" - {

    "must retrieve a single Saved User Answers record" in {
      val now            = Instant.now
      val stubClock      = Clock.fixed(now, ZoneId.systemDefault())
      val answers      = arbitrary[SavedUserAnswers].sample.value
      val insertResult   = answers
      val mockRepository = mock[SaveForLaterRepository]
      val vrn = arbitrary[Vrn].sample.value
      val period = arbitrary[Period].sample.value

      when(mockRepository.get(any(), any())) thenReturn Future.successful(Some(answers))
      val service = new SaveForLaterService(mockRepository, stubClock)

      val result = service.get(vrn, period).futureValue
      result mustBe Some(answers)
      verify(mockRepository, times(1)).get(vrn, period)

    }
  }
}
