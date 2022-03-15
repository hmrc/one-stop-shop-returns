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

import controllers.actions.{AuthAction, GetRegistrationActionProvider}
import models.Period
import models.financialdata.{CurrentPayments, Payment}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.FinancialDataService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.{Clock, LocalDate}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class FinancialDataController @Inject()(
                                         cc: ControllerComponents,
                                         service: FinancialDataService,
                                         auth: AuthAction,
                                         getRegistration: GetRegistrationActionProvider,
                                         clock: Clock
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

  def getOutstandingAmounts: Action[AnyContent] = auth.async {
    implicit request =>
      service.getOutstandingAmounts(request.vrn).map { data =>
        Ok(Json.toJson(data))
      }
  }

  def getVatReturnWithFinancialData(commencementDate: LocalDate): Action[AnyContent] = auth.async {
    implicit request =>
      service.getVatReturnWithFinancialData(request.vrn, commencementDate).map {
        data => Ok(Json.toJson(data))
      }
  }

  def prepareFinancialData(vrn: String): Action[AnyContent] = (auth andThen getRegistration(vrn)).async {
    implicit request =>
      if (request.vrn.vrn == vrn) {
        for {
          vatReturnsWithFinancialData <- service.getVatReturnWithFinancialData(request.vrn, request.registration.commencementDate)
        } yield {

          val filteredPeriodsWithOutstandingAmounts = service
            .filterIfPaymentIsOutstanding(vatReturnsWithFinancialData)
          val duePeriodsWithOutstandingAmounts =
            filteredPeriodsWithOutstandingAmounts.filterNot(_.vatReturn.period.isOverdue(clock))
          val overduePeriodsWithOutstandingAmounts =
            filteredPeriodsWithOutstandingAmounts.filter(_.vatReturn.period.isOverdue(clock))

          val duePayments = duePeriodsWithOutstandingAmounts.map(
            duePeriods =>
              Payment.fromVatReturnWithFinancialData(duePeriods)
          )

          val overduePayments = overduePeriodsWithOutstandingAmounts.map(
            overdue =>
              Payment.fromVatReturnWithFinancialData(overdue)
          )

          Ok(Json.toJson(CurrentPayments(duePayments, overduePayments)))
        }
      } else {
        Future.successful(Unauthorized("Vrn doesn't match"))
      }

  }
}
