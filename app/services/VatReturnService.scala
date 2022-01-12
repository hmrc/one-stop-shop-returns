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

package services

import config.AppConfig
import connectors.CoreVatReturnConnector
import logging.Logging
import models.{PaymentReference, Period, ReturnReference, VatReturn}
import models.corrections.CorrectionPayload
import models.requests.{VatReturnRequest, VatReturnWithCorrectionRequest}
import repositories.VatReturnRepository
import uk.gov.hmrc.domain.Vrn

import java.time.{Clock, Instant}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class VatReturnService @Inject()(
                                  repository: VatReturnRepository,
                                  coreVatReturnService: CoreVatReturnService,
                                  coreVatReturnConnector: CoreVatReturnConnector,
                                  appConfig: AppConfig,
                                  clock: Clock
                                )
                                (implicit ec: ExecutionContext) extends Logging {

  def createVatReturn(request: VatReturnRequest): Future[Option[VatReturn]] = {
    val vatReturn = VatReturn(
      vrn = request.vrn,
      period = request.period,
      reference = ReturnReference(request.vrn, request.period),
      paymentReference = PaymentReference(request.vrn, request.period),
      startDate = request.startDate,
      endDate = request.endDate,
      salesFromNi = request.salesFromNi,
      salesFromEu = request.salesFromEu,
      submissionReceived = Instant.now(clock),
      lastUpdated = Instant.now(clock)
    )

    val emptyCorrectionPayload = CorrectionPayload(
      request.vrn,
      request.period,
      List.empty,
      submissionReceived = Instant.now(clock),
      lastUpdated = Instant.now(clock)
    )

    val insertPayload = repository.insert(vatReturn)

    sendToCoreIfEnabled(vatReturn, emptyCorrectionPayload, insertPayload)
  }

  def createVatReturnWithCorrection(request: VatReturnWithCorrectionRequest): Future[Option[(VatReturn, CorrectionPayload)]] = {
    val vatReturn = VatReturn(
      vrn = request.vatReturnRequest.vrn,
      period = request.vatReturnRequest.period,
      reference = ReturnReference(request.vatReturnRequest.vrn, request.vatReturnRequest.period),
      paymentReference = PaymentReference(request.vatReturnRequest.vrn, request.vatReturnRequest.period),
      startDate = request.vatReturnRequest.startDate,
      endDate = request.vatReturnRequest.endDate,
      salesFromNi = request.vatReturnRequest.salesFromNi,
      salesFromEu = request.vatReturnRequest.salesFromEu,
      submissionReceived = Instant.now(clock),
      lastUpdated = Instant.now(clock)
    )

    val correctionPayload = CorrectionPayload(
      request.correctionRequest.vrn,
      request.correctionRequest.period,
      request.correctionRequest.corrections,
      submissionReceived = Instant.now(clock),
      lastUpdated = Instant.now(clock)
    )

    val insertPayload = repository.insert(vatReturn, correctionPayload)

    sendToCoreIfEnabled(vatReturn, correctionPayload, insertPayload)
  }

  private def sendToCoreIfEnabled[A](vatReturn: VatReturn, correctionPayload: CorrectionPayload, block: => Future[A]): Future[A] = {
    if (appConfig.coreVatReturnsEnabled) {
      val coreVatReturn = coreVatReturnService.toCore(vatReturn, correctionPayload)
      coreVatReturnConnector.submit(coreVatReturn).flatMap {
        case Right(_) =>
          logger.info("Successful submission of vat return to core")
          block
        case Left(coreErrorResponse) =>
          logger.error(s"Error occurred while submitting to core $coreErrorResponse", coreErrorResponse.asException)
          Future.failed(new Exception(coreErrorResponse.asException))
      }
    } else {
      logger.info("Skipping submission of vat return to core")
      block
    }
  }

  def get(vrn: Vrn): Future[Seq[VatReturn]] =
    repository.get(vrn)

  def get(vrn: Vrn, period: Period): Future[Option[VatReturn]] =
    repository.get(vrn, period)
}
