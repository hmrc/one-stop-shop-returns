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
import models.core.CoreErrorResponse
import models.core.CoreErrorResponse.REGISTRATION_NOT_FOUND
import models.{PaymentReference, Period, ReturnReference, VatReturn}
import models.corrections.CorrectionPayload
import models.requests.{VatReturnRequest, VatReturnWithCorrectionRequest}
import repositories.VatReturnRepository
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.http.HeaderCarrier

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

  def createVatReturn(request: VatReturnRequest)(implicit hc: HeaderCarrier): Future[Either[CoreErrorResponse, Option[VatReturn]]] = {
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

    sendToCoreIfEnabled(vatReturn, emptyCorrectionPayload, repository.insert(vatReturn))
  }

  private def sendToCoreIfEnabled[A](vatReturn: VatReturn, correctionPayload: CorrectionPayload, block: => Future[A])(implicit hc: HeaderCarrier): Future[Either[CoreErrorResponse, A]] = {
    if (appConfig.coreVatReturnsEnabled) {

      for {
        coreVatReturn <- coreVatReturnService.toCore(vatReturn, correctionPayload)
        submissionResult <- coreVatReturnConnector.submit(coreVatReturn).flatMap {
          case Right(_) =>
            logger.info("Successful submission of vat return to core")
            block.map(
              payload => Right(payload)
            )
          case Left(coreErrorResponse) if(coreErrorResponse.error == REGISTRATION_NOT_FOUND) =>
            logger.error(s"Error occurred while submitting to core $coreErrorResponse", coreErrorResponse.asException)
            Future.successful(Left(coreErrorResponse)) //TODO dont leave this as string
          case Left(coreErrorResponse) if(coreErrorResponse.error != REGISTRATION_NOT_FOUND) =>
            logger.error(s"Error occurred while submitting to core $coreErrorResponse", coreErrorResponse.asException)
            Future.failed(new Exception(coreErrorResponse.asException))
        }
      } yield submissionResult

    } else {
      logger.info("Skipping submission of vat return to core")
      block.map(
        payload => Right(payload)
      )
    }
  }

  def createVatReturnWithCorrection(request: VatReturnWithCorrectionRequest)(implicit hc: HeaderCarrier): Future[Either[CoreErrorResponse, Option[(VatReturn, CorrectionPayload)]]] = {
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

    sendToCoreIfEnabled(vatReturn, correctionPayload, repository.insert(vatReturn, correctionPayload))
  }

  def get(): Future[Seq[VatReturn]] =
    repository.get()

  def get(vrn: Vrn): Future[Seq[VatReturn]] =
    repository.get(vrn)

  def get(vrn: Vrn, period: Period): Future[Option[VatReturn]] =
    repository.get(vrn, period)
}
