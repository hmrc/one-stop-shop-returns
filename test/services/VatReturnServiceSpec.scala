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
import models.VatReturn
import models.corrections.CorrectionPayload
import models.requests.{VatReturnRequest, VatReturnWithCorrectionRequest}
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
import repositories.VatReturnRepository

import java.time.{Clock, Instant, ZoneId}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class VatReturnServiceSpec
  extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with ScalaCheckPropertyChecks
    with Generators
    with OptionValues
    with ScalaFutures {

  ".createVatReturn" - {

    "must create a VAT return, attempt to save it to the repository, and respond with the result of saving" in {

      val now            = Instant.now
      val stubClock      = Clock.fixed(now, ZoneId.systemDefault())
      val vatReturn      = arbitrary[VatReturn].sample.value
      val insertResult   = Gen.oneOf(Some(vatReturn), None).sample.value
      val mockRepository = mock[VatReturnRepository]

      when(mockRepository.insert(any())) thenReturn Future.successful(insertResult)

      val request = arbitrary[VatReturnRequest].sample.value
      val service = new VatReturnService(mockRepository, stubClock)

      val result = service.createVatReturn(request).futureValue

      result mustEqual insertResult
      verify(mockRepository, times(1)).insert(any())
    }
  }

  ".createVatReturnWithCorrection" - {

    "must create a VAT return and correction, attempt to save it to the repositories, and respond with the result of saving" in {
      val now            = Instant.now
      val stubClock      = Clock.fixed(now, ZoneId.systemDefault())
      val vatReturn      = arbitrary[VatReturn].sample.value
      val correctionPayload      = arbitrary[CorrectionPayload].sample.value
      val insertResult   = Gen.oneOf(Some((vatReturn, correctionPayload)), None).sample.value
      val mockRepository = mock[VatReturnRepository]

      when(mockRepository.insert(any(), any())) thenReturn Future.successful(insertResult)

      val request = arbitrary[VatReturnWithCorrectionRequest].sample.value
      val service = new VatReturnService(mockRepository, stubClock)

      val result = service.createVatReturnWithCorrection(request).futureValue

      result mustEqual insertResult
      verify(mockRepository, times(1)).insert(any(), any())
    }

  }
}
