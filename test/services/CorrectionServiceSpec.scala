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
import models.corrections.CorrectionPayload
import models.Period
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import repositories.CorrectionRepository
import uk.gov.hmrc.domain.Vrn

import java.time.{Clock, Instant, ZoneId}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CorrectionServiceSpec
  extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with ScalaCheckPropertyChecks
    with Generators
    with OptionValues
    with ScalaFutures {

  ".get vrn" - {

    "must get a list of corrections" in {

      val vrn = arbitrary[Vrn].sample.value

      val now = Instant.now
      val stubClock = Clock.fixed(now, ZoneId.systemDefault())
      val mockRepository = mock[CorrectionRepository]
      val correctionPayloads = Gen.listOfN(2, arbitrary[CorrectionPayload]).sample.value

      when(mockRepository.get(any())) thenReturn Future.successful(correctionPayloads)

      val service = new CorrectionService(mockRepository, stubClock)

      val result = service.get(vrn).futureValue

      result mustEqual correctionPayloads
      verify(mockRepository, times(1)).get(any())
    }

  }

  ".get vrn, period" - {

    "must return a single correction" in {

      val vrn = arbitrary[Vrn].sample.value
      val period = arbitrary[Period].sample.value

      val now = Instant.now
      val stubClock = Clock.fixed(now, ZoneId.systemDefault())
      val mockRepository = mock[CorrectionRepository]
      val correctionPayload = arbitrary[CorrectionPayload].sample.value

      when(mockRepository.get(any(), any())) thenReturn Future.successful(Some(correctionPayload))

      val service = new CorrectionService(mockRepository, stubClock)

      val result = service.get(vrn, period).futureValue

      result mustBe Some(correctionPayload)
      verify(mockRepository, times(1)).get(any(), any())
    }

  }

  ".getByCorrectionPeriod" - {

    "must return a list of corrections" in {

      val vrn = arbitrary[Vrn].sample.value
      val period = arbitrary[Period].sample.value

      val now = Instant.now
      val stubClock = Clock.fixed(now, ZoneId.systemDefault())
      val mockRepository = mock[CorrectionRepository]
      val correctionPayload = arbitrary[CorrectionPayload].sample.value

      when(mockRepository.getByCorrectionPeriod(any(), any())) thenReturn Future.successful(List(correctionPayload))

      val service = new CorrectionService(mockRepository, stubClock)

      val result = service.getByCorrectionPeriod(vrn, period).futureValue

      result mustBe List(correctionPayload)
      verify(mockRepository, times(1)).getByCorrectionPeriod(any(), any())
    }

  }
}
