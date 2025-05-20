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

package controllers

import connectors.{RegistrationConnector, VatReturnConnector}
import controllers.actions.AuthAction
import models.Period
import models.core.CoreErrorResponse
import models.etmp.EtmpObligationsQueryParameters
import models.requests.{VatReturnRequest, VatReturnWithCorrectionRequest}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.{PeriodService, VatReturnService}
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import utils.Formatters.etmpDateFormatter

import java.time.{Clock, LocalDate}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class VatReturnController @Inject()(
                                     cc: ControllerComponents,
                                     vatReturnService: VatReturnService,
                                     periodService: PeriodService,
                                     vatReturnConnector: VatReturnConnector,
                                     registrationConnector: RegistrationConnector,
                                     auth: AuthAction,
                                     clock: Clock
                                   )(implicit ec: ExecutionContext)
  extends BackendController(cc) {

  def post(): Action[VatReturnRequest] = auth(parse.json[VatReturnRequest]).async {
    implicit request =>
      vatReturnService.createVatReturn(request.body).map {
        case Right(Some(vatReturn)) => Created(Json.toJson(vatReturn))
        case Right(None) => Conflict
        case Left(errorResponse) if (errorResponse.errorDetail.errorCode == CoreErrorResponse.REGISTRATION_NOT_FOUND) => NotFound(Json.toJson(errorResponse.errorDetail))
        case Left(errorResponse) => ServiceUnavailable(Json.toJson(errorResponse.errorDetail))
      }
  }

  def postWithCorrection(): Action[VatReturnWithCorrectionRequest] = auth(parse.json[VatReturnWithCorrectionRequest]).async {
    implicit request =>
      vatReturnService.createVatReturnWithCorrection(request.body).map {
        case Right(Some(vatReturnWithCorrection)) => Created(Json.toJson(vatReturnWithCorrection))
        case Right(None) => Conflict
        case Left(errorResponse) if (errorResponse.errorDetail.errorCode == CoreErrorResponse.REGISTRATION_NOT_FOUND) => NotFound(Json.toJson(errorResponse.errorDetail))
        case Left(errorResponse) => ServiceUnavailable(Json.toJson(errorResponse.errorDetail))
      }
  }

  // TODO -> Remove via VEOSS-1998
  def list(): Action[AnyContent] = auth.async {
    implicit request =>
      vatReturnService.get(request.vrn).map {
        case Nil => NotFound
        case seq => Ok(Json.toJson(seq))
      }
  }

  def get(period: Period): Action[AnyContent] = auth.async {
    implicit request =>
      vatReturnService.get(request.vrn, period).map {
        case None => NotFound
        case value => Ok(Json.toJson(value))
      }
  }

  def getEtmpVatReturn(period: Period): Action[AnyContent] = auth.async {
    implicit request =>
      vatReturnConnector.get(request.vrn, period).map {
        case Right(etmpVatReturn) => Ok(Json.toJson(etmpVatReturn))
        case Left(errorResponse) => InternalServerError(Json.toJson(errorResponse.body))
      }
  }

  def getObligations(vrn: String): Action[AnyContent] = auth.async {
    implicit request =>

      registrationConnector.getRegistration(Vrn(vrn)).flatMap {
        case Some(registration) =>

          val toDate = if (LocalDate.now(clock).isBefore(registration.commencementDate)) {
            periodService.getRunningPeriod(registration.commencementDate).lastDay
          } else {
            periodService.getRunningPeriod(LocalDate.now(clock)).lastDay
          }

          val queryParameters = EtmpObligationsQueryParameters(
            fromDate = registration.commencementDate.format(etmpDateFormatter),
            toDate = toDate.format(etmpDateFormatter),
            status = None
          )

          vatReturnConnector.getObligations(vrn, queryParameters = queryParameters).map {
            case Right(etmpObligations) => Ok(Json.toJson(etmpObligations))
            case Left(errorResponse) => InternalServerError(Json.toJson(errorResponse.body))
          }
        case _ =>
          Future.successful(NotFound)
      }
  }
}
