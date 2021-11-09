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

package controllers

import controllers.actions.AuthAction
import models.requests.{CorrectionRequest, VatReturnRequest}
import models.Period
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.CorrectionService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class CorrectionController @Inject()(
                                      cc: ControllerComponents,
                                      correctionService: CorrectionService,
                                      auth: AuthAction
                                    )(implicit ec: ExecutionContext)
  extends BackendController(cc) {

  def post(): Action[CorrectionRequest] = auth(parse.json[CorrectionRequest]).async {
    implicit request =>
      correctionService.createCorrection(request.body).map {
        case Some(vatReturn) => Created(Json.toJson(vatReturn))
        case None => Conflict
      }
  }

  def list(): Action[AnyContent] = auth.async {
    implicit request =>
      correctionService.get(request.vrn).map {
        case Nil => NotFound
        case seq => Ok(Json.toJson(seq))
      }
  }

  def get(period: Period): Action[AnyContent] = auth.async {
    implicit request =>
      correctionService.get(request.vrn, period).map {
        case None => NotFound
        case value => Ok(Json.toJson(value))
      }
  }
}
