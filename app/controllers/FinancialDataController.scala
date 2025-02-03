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

import controllers.actions.AuthenticatedControllerComponents
import models.Period
import models.financialdata.{CurrentPayments, Payment, PaymentStatus}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import services.FinancialDataService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.{Clock, LocalDate}
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class FinancialDataController @Inject()(
                                         cc: AuthenticatedControllerComponents,
                                         service: FinancialDataService,
                                         clock: Clock
                                       )(implicit ec: ExecutionContext) extends BackendController(cc) {

  def get(commencementDate: LocalDate): Action[AnyContent] = cc.auth.async {
    implicit request =>
      service.getFinancialData(request.vrn, commencementDate).map { data =>
        Ok(Json.toJson(data))
      }
  }

  def getCharge(period: Period): Action[AnyContent] = cc.auth.async {
    implicit request =>
      service.getCharge(request.vrn, period).map { data =>
        Ok(Json.toJson(data))
      }
  }

  def getOutstandingAmounts: Action[AnyContent] = cc.auth.async {
    implicit request =>
      service.getOutstandingAmounts(request.vrn).map { data =>
        Ok(Json.toJson(data))
      }
  }

  def prepareFinancialData(vrn: String): Action[AnyContent] = cc.authAndGetRegistration(vrn).async {
    implicit request =>
      for {
        vatReturnsWithFinancialData <- service.getVatReturnWithFinancialData(request.vrn, request.registration.commencementDate)
      } yield {

        val filteredPeriodsWithOutstandingAmounts = service
          .filterIfPaymentIsOutstanding(vatReturnsWithFinancialData)

        val duePeriodsWithOutstandingAmounts =
          filteredPeriodsWithOutstandingAmounts.filterNot(_.period.isOverdue(clock))

        val overduePeriodsWithOutstandingAmounts =
          filteredPeriodsWithOutstandingAmounts.filter(_.period.isOverdue(clock))

        val duePayments = duePeriodsWithOutstandingAmounts.map(
          duePeriods =>
            Payment.fromVatReturnWithFinancialData(duePeriods, request.registration.excludedTrader, clock)
        )

        val overduePayments = overduePeriodsWithOutstandingAmounts.map(
          overdue =>
            Payment.fromVatReturnWithFinancialData(overdue, request.registration.excludedTrader, clock)
        )

        val excludedPayments = overduePayments.filter(_.paymentStatus == PaymentStatus.Excluded)
        val overduePaymentsNotExcluded = overduePayments.filterNot(_.paymentStatus == PaymentStatus.Excluded)

        val completedAmounts = service.filterIfPaymentIsComplete(vatReturnsWithFinancialData)
        val completedPayments = completedAmounts.map(
          completedAmount =>
            Payment.fromVatReturnWithFinancialData(completedAmount, request.registration.excludedTrader, clock)
        )

        val totalAmountOverdue = overduePayments.map(_.amountOwed).sum
        val totalAmountOwed = duePayments.map(_.amountOwed).sum + totalAmountOverdue

        Ok(Json.toJson(CurrentPayments(
          duePayments, overduePaymentsNotExcluded, excludedPayments, completedPayments, totalAmountOwed, totalAmountOverdue
        )))
      }

  }
}
