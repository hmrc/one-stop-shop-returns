/*
 * Copyright 2024 HM Revenue & Customs
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

import config.AppConfig
import connectors.CoreVatReturnConnector
import controllers.actions.AuthorisedRequest
import logging.Logging
import models.audit.{CoreVatReturnAuditModel, SubmissionResult}
import models.core.EisErrorResponse
import models.corrections.CorrectionPayload
import models.requests.{VatReturnRequest, VatReturnWithCorrectionRequest}
import models.{PaymentReference, Period, ReturnReference, VatReturn}
import repositories.VatReturnRepository
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.http.HeaderCarrier
import utils.FutureSyntax.FutureOps

import java.time.{Clock, Instant}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}


class VatReturnService @Inject()(
                                  repository: VatReturnRepository,
                                  coreVatReturnService: CoreVatReturnService,
                                  auditService: AuditService,
                                  coreVatReturnConnector: CoreVatReturnConnector,
                                  appConfig: AppConfig,
                                  clock: Clock
                                )(implicit ec: ExecutionContext) extends Logging {

  def createVatReturn(vatReturnRequest: VatReturnRequest)
                     (implicit hc: HeaderCarrier, request: AuthorisedRequest[?]): Future[Either[EisErrorResponse, Option[VatReturn]]] = {
    val vatReturn = VatReturn(
      vrn = vatReturnRequest.vrn,
      period = vatReturnRequest.period,
      reference = ReturnReference(vatReturnRequest.vrn, vatReturnRequest.period),
      paymentReference = PaymentReference(vatReturnRequest.vrn, vatReturnRequest.period),
      startDate = vatReturnRequest.startDate,
      endDate = vatReturnRequest.endDate,
      salesFromNi = vatReturnRequest.salesFromNi,
      salesFromEu = vatReturnRequest.salesFromEu,
      submissionReceived = Instant.now(clock),
      lastUpdated = Instant.now(clock)
    )

    val emptyCorrectionPayload = CorrectionPayload(
      vatReturnRequest.vrn,
      vatReturnRequest.period,
      List.empty,
      submissionReceived = Instant.now(clock),
      lastUpdated = Instant.now(clock)
    )

    sendToCoreIfEnabled(vatReturn, emptyCorrectionPayload, repository.insert(vatReturn))
  }

  private def sendToCoreIfEnabled[A](vatReturn: VatReturn, correctionPayload: CorrectionPayload, block: => Future[A])
                                    (implicit hc: HeaderCarrier, request: AuthorisedRequest[?]): Future[Either[EisErrorResponse, A]] = {
    if (appConfig.coreVatReturnsEnabled) {

      for {
        coreVatReturn <- coreVatReturnService.toCore(vatReturn, correctionPayload)
        submissionResult <- coreVatReturnConnector.submit(coreVatReturn).flatMap {
          case Right(_) =>
            logger.info("Successful submission of vat return to core")
            auditService.audit(CoreVatReturnAuditModel.build(coreVatReturn, SubmissionResult.Success, None))
            block.map(
              payload => Right(payload)
            )
          case Left(eisErrorResponse) =>
            logger.error(s"Error occurred while submitting to core $eisErrorResponse", eisErrorResponse.errorDetail.asException)
            auditService.audit(CoreVatReturnAuditModel.build(coreVatReturn, SubmissionResult.Failure, Some(eisErrorResponse.errorDetail)))
            Left(eisErrorResponse).toFuture
        }
      } yield submissionResult

    } else {
      logger.info("Skipping submission of vat return to core")
      block.map(
        payload => Right(payload)
      )
    }
  }

  def createVatReturnWithCorrection(vatReturnWithCorrectionRequest: VatReturnWithCorrectionRequest)
                                   (implicit hc: HeaderCarrier, request: AuthorisedRequest[?]): Future[Either[EisErrorResponse, Option[(VatReturn, CorrectionPayload)]]] = {
    val vatReturn = VatReturn(
      vrn = vatReturnWithCorrectionRequest.vatReturnRequest.vrn,
      period = vatReturnWithCorrectionRequest.vatReturnRequest.period,
      reference = ReturnReference(vatReturnWithCorrectionRequest.vatReturnRequest.vrn, vatReturnWithCorrectionRequest.vatReturnRequest.period),
      paymentReference = PaymentReference(vatReturnWithCorrectionRequest.vatReturnRequest.vrn, vatReturnWithCorrectionRequest.vatReturnRequest.period),
      startDate = vatReturnWithCorrectionRequest.vatReturnRequest.startDate,
      endDate = vatReturnWithCorrectionRequest.vatReturnRequest.endDate,
      salesFromNi = vatReturnWithCorrectionRequest.vatReturnRequest.salesFromNi,
      salesFromEu = vatReturnWithCorrectionRequest.vatReturnRequest.salesFromEu,
      submissionReceived = Instant.now(clock),
      lastUpdated = Instant.now(clock)
    )

    val correctionPayload = CorrectionPayload(
      vatReturnWithCorrectionRequest.correctionRequest.vrn,
      vatReturnWithCorrectionRequest.correctionRequest.period,
      vatReturnWithCorrectionRequest.correctionRequest.corrections,
      submissionReceived = Instant.now(clock),
      lastUpdated = Instant.now(clock)
    )

    sendToCoreIfEnabled(vatReturn, correctionPayload, repository.insert(vatReturn, correctionPayload))
  }

  def get(vrn: Vrn): Future[Seq[VatReturn]] =
    repository.get(vrn)

  def get(vrn: Vrn, period: Period): Future[Option[VatReturn]] =
    repository.get(vrn, period)
}
