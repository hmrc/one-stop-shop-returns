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
import models.{Period, PeriodWithStatus, SubmissionStatus}
import models.requests.VatReturnRequest
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.{PeriodService, VatReturnService}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.{Clock, LocalDate}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ReturnStatusController @Inject()(
                                     cc: ControllerComponents,
                                     vatReturnService: VatReturnService,
                                     periodService: PeriodService,
                                     auth: AuthAction,
                                     clock: Clock
                                   )(implicit ec: ExecutionContext)
  extends BackendController(cc) {

  def listStatuses(commencementDate: Long): Action[AnyContent] = auth.async {
    implicit request =>
      val commencementLocalDate = LocalDate.ofEpochDay(commencementDate)
      val periodWithStatuses = Future.sequence (
        periodService.getReturnPeriods(commencementLocalDate).map { period =>
          vatReturnService.get(request.vrn, period).map {
            case Some(_) =>
              PeriodWithStatus(period, SubmissionStatus.Complete)
            case None =>
              if (LocalDate.now(clock).isAfter(period.paymentDeadline)) {
                PeriodWithStatus(period, SubmissionStatus.Overdue)
              } else {
                PeriodWithStatus(period, SubmissionStatus.Due)
              }
          }
        }
      )

      periodWithStatuses.map{ pws =>
        Ok(Json.toJson(pws))
      }
  }
}
