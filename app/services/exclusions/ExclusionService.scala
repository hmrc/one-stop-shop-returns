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

package services.exclusions

import logging.Logging
import models.exclusions.ExcludedTrader
import models.requests.RegistrationRequest
import models.PeriodWithStatus
import models.SubmissionStatus.Complete
import play.api.mvc.AnyContent

import javax.inject.Inject

class ExclusionService @Inject()() extends Logging {

  def hasSubmittedFinalReturn(availablePeriodsWithStatus: Seq[PeriodWithStatus])(implicit request: RegistrationRequest[AnyContent]): Boolean = {
    request.registration.excludedTrader match {
      case Some(excludedTrader: ExcludedTrader) =>
        availablePeriodsWithStatus.exists { periodWithStatus =>
          periodWithStatus.status == Complete &&
            periodWithStatus.period == excludedTrader.finalReturnPeriod
        }
      case _ => false
    }
  }
}
