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

package controllers

import controllers.actions.AuthAction
import models.requests.{VatReturnRequest, VatReturnWithCorrectionRequest}
import models.Period
import models.core.CoreErrorResponse
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.VatReturnService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class VatReturnController @Inject()(
                                     cc: ControllerComponents,
                                     vatReturnService: VatReturnService,
                                     auth: AuthAction
                                   )(implicit ec: ExecutionContext)
  extends BackendController(cc) {

  def post(): Action[VatReturnRequest] = auth(parse.json[VatReturnRequest]).async {
    implicit request =>
      vatReturnService.createVatReturn(request.body).map {
        case Right(Some(vatReturn)) => Created(Json.toJson(vatReturn))
        case Right(None) => Conflict
        case Left(errorResponse) if(errorResponse.errorDetail.error == CoreErrorResponse.REGISTRATION_NOT_FOUND) => NotFound(Json.toJson(errorResponse.errorDetail))
        case Left(errorResponse) => ServiceUnavailable(Json.toJson(errorResponse))
      }
  }

  def postWithCorrection(): Action[VatReturnWithCorrectionRequest] = auth(parse.json[VatReturnWithCorrectionRequest]).async {
    implicit request =>
      vatReturnService.createVatReturnWithCorrection(request.body).map {
        case Right(Some(vatReturnWithCorrection)) => Created(Json.toJson(vatReturnWithCorrection))
        case Right(None) => Conflict
        case Left(errorResponse) if(errorResponse.errorDetail.error == CoreErrorResponse.REGISTRATION_NOT_FOUND) => NotFound(Json.toJson(errorResponse.errorDetail))
        case Left(errorResponse) => ServiceUnavailable(Json.toJson(errorResponse))
      }
  }

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
}
