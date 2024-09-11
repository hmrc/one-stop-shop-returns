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

package controllers.actions

import connectors.RegistrationConnector
import logging.Logging
import models.requests.RegistrationRequest
import play.api.mvc.Results.{NotFound, Unauthorized}
import play.api.mvc.{ActionRefiner, Result}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class GetRegistrationAction @Inject()(
                                       maybeVrn: Option[String],
                                       val registrationConnector: RegistrationConnector
                                     )(implicit val executionContext: ExecutionContext)
  extends ActionRefiner[AuthorisedRequest, RegistrationRequest] with Logging {


  override protected def refine[A](request: AuthorisedRequest[A]): Future[Either[Result, RegistrationRequest[A]]] = {
    maybeVrn match {
      case Some(vrn) if vrn == request.vrn.vrn =>
          getRegistrationFromRequest(request)
      case Some(_) =>
        logger.error("VRNs did not match")
        Future.successful(Left(Unauthorized("VRNs do not match")))
      case _ =>
        getRegistrationFromRequest(request)
    }
  }

  private def getRegistrationFromRequest[A](request: AuthorisedRequest[A]) = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
    registrationConnector.getRegistration(request.vrn) flatMap {
      case Some(registration) =>
        Future.successful(Right(RegistrationRequest(request.request, request.vrn, registration)))
      case None =>
        logger.error(s"User had enrolment, but no registration found for VRN ${request.vrn}")
        Future.successful(Left(NotFound("Not found registration")))
    }
  }
}

class GetRegistrationActionProvider @Inject()(registrationConnector: RegistrationConnector)
                                           (implicit ec: ExecutionContext) {

  def apply(vrn: String): GetRegistrationAction =
    new GetRegistrationAction(Some(vrn), registrationConnector)

  def apply(): GetRegistrationAction =
    new GetRegistrationAction(None, registrationConnector)
}