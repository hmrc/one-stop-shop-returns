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

import controllers.actions.AuthenticatedControllerComponents
import models.SubmissionStatus.Complete
import models.yourAccount._
import models.{PeriodWithStatus, SubmissionStatus}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import repositories.SaveForLaterRepository
import services.{PeriodService, VatReturnService}
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.{Clock, LocalDate}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ReturnStatusController @Inject()(
                                        cc: AuthenticatedControllerComponents,
                                        vatReturnService: VatReturnService,
                                        periodService: PeriodService,
                                        saveForLaterRepository: SaveForLaterRepository,
                                        clock: Clock
                                      )(implicit ec: ExecutionContext)
  extends BackendController(cc) {

  def listStatuses(commencementLocalDate: LocalDate): Action[AnyContent] = cc.auth.async {
    implicit request =>
      val periodWithStatuses = getStatuses(commencementLocalDate, request.vrn)

      periodWithStatuses.map { pws =>
        Ok(Json.toJson(pws))
      }
  }

  def getCurrentReturns(vrn: String): Action[AnyContent] = cc.authAndGetRegistration(vrn).async {
    implicit request =>
      for {
        availablePeriodsWithStatus <- getStatuses(request.registration.commencementDate, request.vrn)
        savedAnswers <- saveForLaterRepository.get(request.vrn)
      } yield {
        val answers = savedAnswers.sortBy(_.lastUpdated).lastOption

        val incompletePeriods = availablePeriodsWithStatus.filterNot(_.status == Complete)

        val periodInProgress = answers.map(answer => answer.period)
        val oldestPeriod = incompletePeriods.sortBy(_.period).headOption
        val returns = incompletePeriods.sortBy(_.period).map(
          periodWithStatus => Return.fromPeriod(
            periodWithStatus.period,
            periodWithStatus.status,
            periodInProgress.contains(periodWithStatus.period),
            oldestPeriod.contains(periodWithStatus)
          )
        )

        Ok(Json.toJson(returns))
      }
  }

  private def getStatuses(commencementLocalDate: LocalDate, vrn: Vrn): Future[Seq[PeriodWithStatus]] = {
    val periods = periodService.getReturnPeriods(commencementLocalDate)
    vatReturnService.get(vrn).map {
      returns =>
        val returnPeriods = returns.map(_.period)
        periods.map {
          period =>
            if (returnPeriods.contains(period)) {
              PeriodWithStatus(period, SubmissionStatus.Complete)
            } else {
              if (LocalDate.now(clock).isAfter(period.paymentDeadline)) {
                PeriodWithStatus(period, SubmissionStatus.Overdue)
              } else {
                PeriodWithStatus(period, SubmissionStatus.Due)
              }
            }
        }
    }
  }

}
