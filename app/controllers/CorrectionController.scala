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

import controllers.actions.{AuthAction, AuthenticatedControllerComponents}
import models.requests.CorrectionRequest
import models.Period
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import services.CorrectionService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class CorrectionController @Inject()(
                                      cc: AuthenticatedControllerComponents,
                                      correctionService: CorrectionService
                                    )(implicit ec: ExecutionContext)
  extends BackendController(cc) {

  def list(): Action[AnyContent] = cc.auth.async {
    implicit request =>
      correctionService.get(request.vrn).map {
        case Nil => NotFound
        case seq => Ok(Json.toJson(seq))
      }
  }

  def get(period: Period): Action[AnyContent] = cc.auth.async {
    implicit request =>
      correctionService.get(request.vrn, period).map {
        case None => NotFound
        case value => Ok(Json.toJson(value))
      }
  }

  def getByCorrectionPeriod(period: Period): Action[AnyContent] = cc.auth.async {
    implicit request =>
      correctionService.getByCorrectionPeriod(request.vrn, period).map {
        case Nil => NotFound
        case value => Ok(Json.toJson(value))
      }
  }
}
