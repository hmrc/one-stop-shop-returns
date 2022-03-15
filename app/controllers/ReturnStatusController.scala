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
import models.SubmissionStatus.{Due, Overdue}
import models.yourAccount._
import models.{PeriodWithStatus, SubmissionStatus}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repositories.SaveForLaterRepository
import services.{PeriodService, VatReturnService}
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.{Clock, LocalDate}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ReturnStatusController @Inject()(
                                        cc: ControllerComponents,
                                        vatReturnService: VatReturnService,
                                        periodService: PeriodService,
                                        saveForLaterRepository: SaveForLaterRepository,
                                        auth: AuthAction,
                                        getRegistration: GetRegistrationActionProvider,
                                        clock: Clock
                                      )(implicit ec: ExecutionContext)
  extends BackendController(cc) {

  def listStatuses(commencementLocalDate: LocalDate): Action[AnyContent] = auth.async {
    implicit request =>
      val periodWithStatuses = getStatuses(commencementLocalDate, request.vrn)

      periodWithStatuses.map { pws =>
        Ok(Json.toJson(pws))
      }
  }

  def getCurrentReturns(vrn: String): Action[AnyContent] = (auth andThen getRegistration(vrn)).async {
    implicit request =>
      if(request.vrn.vrn == vrn) {
        for {
          availablePeriodsWithStatus <- getStatuses(request.registration.commencementDate, request.vrn)
          savedAnswers <- saveForLaterRepository.get(request.vrn)
        } yield {
          val answers = savedAnswers.sortBy(_.lastUpdated).lastOption
          val returnInProgress = answers.map(savedAnswers =>
            Return.fromPeriod(savedAnswers.period))
          val duePeriods = availablePeriodsWithStatus.find(_.status == Due).map(periodWithStatus => Return.fromPeriod(periodWithStatus.period))
          val overduePeriods = availablePeriodsWithStatus.filter(_.status == Overdue).map(periodWithStatus => Return.fromPeriod(periodWithStatus.period))
          val returns = OpenReturns(returnInProgress, duePeriods, overduePeriods)

          Ok(Json.toJson(returns))
        }
      } else {
        Future.successful(Unauthorized)
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
