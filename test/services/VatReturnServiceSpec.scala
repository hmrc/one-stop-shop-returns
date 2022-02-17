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

import base.SpecBase
import config.AppConfig
import connectors.CoreVatReturnConnector
import models.VatReturn
import models.core.{CoreErrorResponse, EisErrorResponse}
import models.core.CoreErrorResponse.REGISTRATION_NOT_FOUND
import models.corrections.CorrectionPayload
import models.requests.{VatReturnRequest, VatReturnWithCorrectionRequest}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import repositories.VatReturnRepository
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class VatReturnServiceSpec
  extends SpecBase {

  private val coreVatReturnService = mock[CoreVatReturnService]
  private val coreVatReturnConnector = mock[CoreVatReturnConnector]
  private val appConfig = mock[AppConfig]
  private val vatReturn = arbitrary[VatReturn].sample.value
  implicit private lazy val hc: HeaderCarrier = HeaderCarrier()

  ".createVatReturn" - {

    "must create a VAT return, attempt to save it to the repository, and respond with the result of saving" in {

      val insertResult = Gen.oneOf(Some(vatReturn), None).sample.value
      val mockRepository = mock[VatReturnRepository]

      when(mockRepository.insert(any())) thenReturn Future.successful(insertResult)

      val request = arbitrary[VatReturnRequest].sample.value
      val service = new VatReturnService(mockRepository, coreVatReturnService, coreVatReturnConnector, appConfig, stubClock)

      val result = service.createVatReturn(request).futureValue

      result mustEqual Right(insertResult)
      verify(mockRepository, times(1)).insert(any())
    }
  }

  ".createVatReturnWithCorrection" - {

    "must create a VAT return and correction, attempt to save it to the repositories, and respond with the result of saving" in {
      val correctionPayload = arbitrary[CorrectionPayload].sample.value
      val insertResult = Gen.oneOf(Some((vatReturn, correctionPayload)), None).sample.value
      val mockRepository = mock[VatReturnRepository]

      when(mockRepository.insert(any(), any())) thenReturn Future.successful(insertResult)

      val request = arbitrary[VatReturnWithCorrectionRequest].sample.value
      val service = new VatReturnService(mockRepository, coreVatReturnService, coreVatReturnConnector, appConfig, stubClock)

      val result = service.createVatReturnWithCorrection(request).futureValue

      result mustEqual Right(insertResult)
      verify(mockRepository, times(1)).insert(any(), any())
    }

    "must error when core enabled and fails to send to core" in {
      val coreErrorResponse = CoreErrorResponse(Instant.now(), None, "ERROR", "There was an error")
      val eisErrorResponse = EisErrorResponse(coreErrorResponse)
      val mockRepository = mock[VatReturnRepository]

      when(appConfig.coreVatReturnsEnabled) thenReturn true
      when(coreVatReturnConnector.submit(any())) thenReturn Future.successful(Left(eisErrorResponse))
      when(coreVatReturnService.toCore(any(), any())(any())) thenReturn Future.successful(coreVatReturn)

      val request = arbitrary[VatReturnWithCorrectionRequest].sample.value
      val service = new VatReturnService(mockRepository, coreVatReturnService, coreVatReturnConnector, appConfig, stubClock)

      val result = service.createVatReturnWithCorrection(request).futureValue

      result mustBe Left(eisErrorResponse)
    }

    "must error when core enabled and registration is not present in core" in {
      val coreErrorResponse = CoreErrorResponse(Instant.now(), None, REGISTRATION_NOT_FOUND, "There was an error")
      val eisErrorResponse = EisErrorResponse(coreErrorResponse)
      val mockRepository = mock[VatReturnRepository]

      when(appConfig.coreVatReturnsEnabled) thenReturn true
      when(coreVatReturnConnector.submit(any())) thenReturn Future.successful(Left(eisErrorResponse))
      when(coreVatReturnService.toCore(any(), any())(any())) thenReturn Future.successful(coreVatReturn)

      val request = arbitrary[VatReturnWithCorrectionRequest].sample.value
      val service = new VatReturnService(mockRepository, coreVatReturnService, coreVatReturnConnector, appConfig, stubClock)

      val result = service.createVatReturnWithCorrection(request).futureValue
      result mustBe Left(eisErrorResponse)

    }

  }
}
