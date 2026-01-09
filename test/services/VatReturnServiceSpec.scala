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
import controllers.actions.AuthorisedRequest
import models.audit.CoreVatReturnAuditModel
import models.audit.SubmissionResult.Success
import models.core.CoreErrorResponse.REGISTRATION_NOT_FOUND
import models.core.{CoreErrorResponse, CorePeriod, CoreVatReturn, EisErrorResponse}
import models.corrections.CorrectionPayload
import models.requests.{VatReturnRequest, VatReturnWithCorrectionRequest}
import models.{Period, VatReturn}
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito
import org.mockito.Mockito.{times, verify, verifyNoInteractions, when}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.AnyContent
import play.api.test.FakeRequest
import repositories.VatReturnRepository
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.http.HeaderCarrier
import utils.FutureSyntax.FutureOps

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class VatReturnServiceSpec extends SpecBase with BeforeAndAfterEach {

  private val mockRepository = mock[VatReturnRepository]
  private val coreVatReturnService = mock[CoreVatReturnService]
  private val coreVatReturnConnector = mock[CoreVatReturnConnector]
  private val mockSaveForLaterService: SaveForLaterService = mock[SaveForLaterService]
  private val appConfig = mock[AppConfig]

  private val vatReturn = arbitrary[VatReturn].sample.value
  private val auditService = mock[AuditService]
  implicit private lazy val hc: HeaderCarrier = HeaderCarrier()
  implicit private lazy val ar: AuthorisedRequest[AnyContent] = AuthorisedRequest(FakeRequest(), userAnswersId, Vrn("123456789"))

  override def beforeEach(): Unit = {
    Mockito.reset(
      mockRepository,
      mockSaveForLaterService,
      auditService,
      coreVatReturnService,
      coreVatReturnConnector,
      appConfig
    )
  }

  "VatReturnService" - {

    ".createVatReturn" - {

      "must create a VAT return, attempt to save it to the repository, and respond with the result of saving" in {

        val insertResult = Gen.oneOf(Some(vatReturn), None).sample.value

        when(mockRepository.insert(any())) `thenReturn` insertResult.toFuture

        val request = arbitrary[VatReturnRequest].sample.value
        val service = new VatReturnService(mockRepository, coreVatReturnService, mockSaveForLaterService, auditService, coreVatReturnConnector, appConfig, stubClock)

        val result = service.createVatReturn(request).futureValue

        result `mustBe` Right(insertResult)
        verify(mockRepository, times(1)).insert(any())
        verifyNoInteractions(mockSaveForLaterService)
        verifyNoInteractions(coreVatReturnService)
        verifyNoInteractions(coreVatReturnConnector)
        verifyNoInteractions(auditService)
      }

      "when core enabled" - {

        "must create a VAT return, attempt to save it to the repository, delete any saved returns for that period and respond with the result of saving" in {

          val insertResult = Gen.oneOf(Some(vatReturn), None).sample.value

          when(appConfig.coreVatReturnsEnabled) `thenReturn` true
          when(mockRepository.insert(any())) `thenReturn` insertResult.toFuture
          when(coreVatReturnConnector.submit(any())) `thenReturn` Right(()).toFuture
          when(coreVatReturnService.toCore(any(), any())(any())) `thenReturn` coreVatReturn.toFuture
          when(mockSaveForLaterService.delete(any(), any())) `thenReturn` true.toFuture

          val request = arbitrary[VatReturnRequest].sample.value
          val service = new VatReturnService(mockRepository, coreVatReturnService, mockSaveForLaterService, auditService, coreVatReturnConnector, appConfig, stubClock)

          val result = service.createVatReturn(request).futureValue

          val convertedPeriod: Period = Period.fromString(coreVatReturn.period.toString).value

          val expectedAuditEvent: CoreVatReturnAuditModel = CoreVatReturnAuditModel(
            userId = userAnswersId,
            userAgent = "",
            vrn = vrn.vrn,
            coreVatReturn = coreVatReturn,
            result = Success,
            errorResponse = None
          )

          result `mustBe` Right(insertResult)
          verify(mockSaveForLaterService, times(1)).delete(eqTo(vrn), eqTo(convertedPeriod))
          verify(mockRepository, times(1)).insert(any())
          verify(coreVatReturnConnector, times(1)).submit(eqTo(coreVatReturn))
          verify(coreVatReturnService, times(1)).toCore(any(), any())(any())
          verify(auditService, times(1)).audit(eqTo(expectedAuditEvent))(any(), any())
        }

        "must throw an Exception when an error occurs converting Core period to Period" in {

          val invalidCoreVatReturn: CoreVatReturn = coreVatReturn
            .copy(period = CorePeriod(year = 2025, quarter = 5))

          when(appConfig.coreVatReturnsEnabled) `thenReturn` true
          when(mockRepository.insert(any())) `thenReturn` None.toFuture
          when(coreVatReturnConnector.submit(any())) `thenReturn` Right(()).toFuture
          when(coreVatReturnService.toCore(any(), any())(any())) `thenReturn` invalidCoreVatReturn.toFuture
          when(mockSaveForLaterService.delete(any(), any())) `thenReturn` false.toFuture

          val request = arbitrary[VatReturnRequest].sample.value
          val service = new VatReturnService(mockRepository, coreVatReturnService, mockSaveForLaterService, auditService, coreVatReturnConnector, appConfig, stubClock)

          val result = service.createVatReturn(request).failed

          whenReady(result) { exp =>
            exp `mustBe` a[Exception]
          }
          verifyNoInteractions(mockSaveForLaterService)
          verifyNoInteractions(mockRepository)
          verify(coreVatReturnConnector, times(1)).submit(eqTo(invalidCoreVatReturn))
          verify(coreVatReturnService, times(1)).toCore(any(), any())(any())
          verifyNoInteractions(auditService)
        }
      }
    }

    ".createVatReturnWithCorrection" - {

      "must create a VAT return and correction, attempt to save it to the repositories, and respond with the result of saving" in {

        val correctionPayload = arbitrary[CorrectionPayload].sample.value
        val insertResult = Gen.oneOf(Some((vatReturn, correctionPayload)), None).sample.value

        when(mockRepository.insert(any(), any())) `thenReturn` insertResult.toFuture

        val request = arbitrary[VatReturnWithCorrectionRequest].sample.value
        val service = new VatReturnService(mockRepository, coreVatReturnService, mockSaveForLaterService, auditService, coreVatReturnConnector, appConfig, stubClock)

        val result = service.createVatReturnWithCorrection(request).futureValue

        result `mustBe` Right(insertResult)
        verify(mockRepository, times(1)).insert(any(), any())
        verifyNoInteractions(mockSaveForLaterService)
        verifyNoInteractions(coreVatReturnService)
        verifyNoInteractions(coreVatReturnConnector)
        verifyNoInteractions(auditService)
      }

      "when core enabled" - {

        "must create a VAT return and correction, attempt to save it to the repositories, delete any saved returns for that period and respond with the result of saving" in {

          val correctionPayload = arbitrary[CorrectionPayload].sample.value
          val insertResult = Gen.oneOf(Some((vatReturn, correctionPayload)), None).sample.value

          when(appConfig.coreVatReturnsEnabled) `thenReturn` true
          when(mockRepository.insert(any(), any())) `thenReturn`insertResult.toFuture
          when(coreVatReturnConnector.submit(any())) `thenReturn` Right(()).toFuture
          when(coreVatReturnService.toCore(any(), any())(any())) `thenReturn` coreVatReturn.toFuture
          when(mockSaveForLaterService.delete(any(), any())) `thenReturn` true.toFuture

          val request = arbitrary[VatReturnWithCorrectionRequest].sample.value
          val service = new VatReturnService(mockRepository, coreVatReturnService, mockSaveForLaterService, auditService, coreVatReturnConnector, appConfig, stubClock)

          val result = service.createVatReturnWithCorrection(request).futureValue

          val convertedPeriod: Period = Period.fromString(coreVatReturn.period.toString).value

          val expectedAuditEvent: CoreVatReturnAuditModel = CoreVatReturnAuditModel(
            userId = userAnswersId,
            userAgent = "",
            vrn = vrn.vrn,
            coreVatReturn = coreVatReturn,
            result = Success,
            errorResponse = None
          )

          result `mustBe` Right(insertResult)
          verify(mockSaveForLaterService, times(1)).delete(eqTo(vrn), eqTo(convertedPeriod))
          verify(mockRepository, times(1)).insert(any(), any())
          verify(coreVatReturnConnector, times(1)).submit(eqTo(coreVatReturn))
          verify(coreVatReturnService, times(1)).toCore(any(), any())(any())
          verify(auditService, times(1)).audit(eqTo(expectedAuditEvent))(any(), any())
        }

        "must throw an Exception when an error occurs converting Core period to Period" in {

          val invalidCoreVatReturn: CoreVatReturn = coreVatReturn
            .copy(period = CorePeriod(year = 2025, quarter = 5))

          when(appConfig.coreVatReturnsEnabled) `thenReturn` true
          when(mockRepository.insert(any())) `thenReturn` None.toFuture
          when(coreVatReturnConnector.submit(any())) `thenReturn` Right(()).toFuture
          when(coreVatReturnService.toCore(any(), any())(any())) `thenReturn` invalidCoreVatReturn.toFuture
          when(mockSaveForLaterService.delete(any(), any())) `thenReturn` false.toFuture

          val request = arbitrary[VatReturnWithCorrectionRequest].sample.value
          val service = new VatReturnService(mockRepository, coreVatReturnService, mockSaveForLaterService, auditService, coreVatReturnConnector, appConfig, stubClock)

          val result = service.createVatReturnWithCorrection(request).failed

          whenReady(result) { exp =>
            exp `mustBe` a[Exception]
          }
          verifyNoInteractions(mockSaveForLaterService)
          verifyNoInteractions(mockRepository)
          verify(coreVatReturnConnector, times(1)).submit(eqTo(invalidCoreVatReturn))
          verify(coreVatReturnService, times(1)).toCore(any(), any())(any())
          verifyNoInteractions(auditService)
        }

        "must error when fails to send to core" in {

          val coreErrorResponse = CoreErrorResponse(Instant.now(), None, "ERROR", "There was an error")
          val eisErrorResponse = EisErrorResponse(coreErrorResponse)

          when(appConfig.coreVatReturnsEnabled) `thenReturn` true
          when(coreVatReturnConnector.submit(any())) `thenReturn` Left(eisErrorResponse).toFuture
          when(coreVatReturnService.toCore(any(), any())(any())) `thenReturn` coreVatReturn.toFuture

          val request = arbitrary[VatReturnWithCorrectionRequest].sample.value
          val service = new VatReturnService(mockRepository, coreVatReturnService, mockSaveForLaterService, auditService, coreVatReturnConnector, appConfig, stubClock)

          val result = service.createVatReturnWithCorrection(request).futureValue

          result `mustBe` Left(eisErrorResponse)
        }

        "must error when registration is not present in core" in {

          val coreErrorResponse = CoreErrorResponse(Instant.now(), None, REGISTRATION_NOT_FOUND, "There was an error")
          val eisErrorResponse = EisErrorResponse(coreErrorResponse)

          when(appConfig.coreVatReturnsEnabled) `thenReturn` true
          when(coreVatReturnConnector.submit(any())) `thenReturn` Left(eisErrorResponse).toFuture
          when(coreVatReturnService.toCore(any(), any())(any())) `thenReturn` coreVatReturn.toFuture

          val request = arbitrary[VatReturnWithCorrectionRequest].sample.value
          val service = new VatReturnService(mockRepository, coreVatReturnService, mockSaveForLaterService, auditService, coreVatReturnConnector, appConfig, stubClock)

          val result = service.createVatReturnWithCorrection(request).futureValue
          result `mustBe` Left(eisErrorResponse)
        }
      }
    }
  }
}
