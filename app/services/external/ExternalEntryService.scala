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

package services.external

import logging.Logging
import models.external._
import models.Period
import repositories.ExternalEntryRepository
import uk.gov.hmrc.play.bootstrap.backend.http.ErrorResponse

import java.time.{Clock, Instant}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ExternalEntryService @Inject()(
                                 externalEntryRepository: ExternalEntryRepository,
                                 clock: Clock
                               )(implicit executionContext: ExecutionContext) extends Logging {

  def getExternalResponse(externalRequest: ExternalRequest,
                          userId: String,
                          page: String,
                          period: Option[Period] = None,
                          language: Option[String] = None): Either[ErrorResponse, ExternalResponse] = {

    val response = (page, period) match {
      case (YourAccount.name, None) =>
        saveReturnUrl(userId, externalRequest)
        Right(ExternalResponse(YourAccount.url))
      case (ReturnsHistory.name, None) =>
        saveReturnUrl(userId, externalRequest)
        Right(ExternalResponse(ReturnsHistory.url))
      case (StartReturn.name, Some(returnPeriod)) =>
        saveReturnUrl(userId, externalRequest)
        Right(ExternalResponse(StartReturn.url(returnPeriod)))
      case (ContinueReturn.name, Some(returnPeriod)) =>
        saveReturnUrl(userId, externalRequest)
        Right(ExternalResponse(ContinueReturn.url(returnPeriod)))
      case (Payment.name, None) =>
        saveReturnUrl(userId, externalRequest)
        Right(ExternalResponse(Payment.url))
      case (url, _) => Left(ErrorResponse(500, s"Unknown external entry $url"))
    }

    (response, language) match {
      case (Right(externalResponse), Some("cy")) => Right(wrapLanguageWarning(externalResponse))
      case _ => response
    }
  }

  def getSavedResponseUrl(userId: String): Future[Option[String]] = {
    externalEntryRepository.get(userId).map(_.map(_.returnUrl))
  }

  private def wrapLanguageWarning(response: ExternalResponse): ExternalResponse = {
    ExternalResponse(NoMoreWelsh.url(response.redirectUrl))
  }

  private def saveReturnUrl(userId: String, externalRequest: ExternalRequest): Future[Boolean] = {
    val externalEntry = ExternalEntry(userId, externalRequest.returnUrl, Instant.now(clock))

    externalEntryRepository.set(externalEntry).map { _ => true }.recover {
      case e: Exception =>
        logger.error(s"An error occurred while saving the external returnUrl in the session, ${e.getMessage}", e)
        false
    }
  }
}
