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

package controllers.actions

import connectors.RegistrationConnector
import controllers.routes
import models.requests.RegistrationRequest
import play.api.mvc.Results.{NotFound, Redirect, Unauthorized}
import play.api.mvc.{ActionRefiner, ActionTransformer, Result}
import uk.gov.hmrc.auth.core.AuthorisationException
import uk.gov.hmrc.http.{HeaderCarrier, UnauthorizedException}
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class GetRegistrationAction @Inject()(
                                       vrn: String,
                                       val registrationConnector: RegistrationConnector
                                     )(implicit val executionContext: ExecutionContext)
  extends ActionRefiner[AuthorisedRequest, RegistrationRequest] {

  override protected def refine[A](request: AuthorisedRequest[A]): Future[Either[Result, RegistrationRequest[A]]] = {
    if(request.vrn.vrn == vrn) {
      registrationConnector.getRegistration(request.vrn) flatMap {
        case Some(registration) =>
          Future.successful(Right(RegistrationRequest(request.request, request.vrn, registration)))
        case None =>
          Future.successful(Left(NotFound("Not found registration")))
      }
    } else {
      Future.successful(Left(Unauthorized("VRNs do not match")))
    }
  }
}

class GetRegistrationActionProvider @Inject()(registrationConnector: RegistrationConnector)
                                           (implicit ec: ExecutionContext) {

  def apply(vrn: String): GetRegistrationAction =
    new GetRegistrationAction(vrn, registrationConnector)
}