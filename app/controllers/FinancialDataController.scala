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
import models.Period
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.FinancialDataService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class FinancialDataController @Inject()(
                                         cc: ControllerComponents,
                                         service: FinancialDataService,
                                         auth: AuthAction
                                       )(implicit ec: ExecutionContext) extends BackendController(cc) {

  def get(commencementDate: LocalDate): Action[AnyContent] = auth.async {
    implicit request =>
      service.getFinancialData(request.vrn, commencementDate).map { data =>
        Ok(Json.toJson(data))
      }
  }

  def getCharge(period: Period): Action[AnyContent] = auth.async {
    implicit request =>
      service.getCharge(request.vrn, period).map { data =>
        Ok(Json.toJson(data))
      }
  }

  def getOutstandingAmounts(commencementDate: LocalDate): Action[AnyContent] = auth.async {
    implicit request =>
      service.getOutstandingAmounts(request.vrn, commencementDate).map { data =>
        Ok(Json.toJson(data))
      }
  }
}