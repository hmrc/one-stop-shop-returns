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

package services

import models.Period
import models.corrections.CorrectionPayload
import repositories.CorrectionRepository
import uk.gov.hmrc.domain.Vrn

import java.time.Clock
import javax.inject.Inject
import scala.concurrent.Future

class CorrectionService @Inject()( repository: CorrectionRepository,
                                   clock: Clock ) {

  def get(): Future[Seq[CorrectionPayload]] =
    repository.get()

  def getByPeriods(periods: Seq[Period]): Future[Seq[CorrectionPayload]] =
    repository.getByPeriods(periods)

  def get(vrn: Vrn): Future[Seq[CorrectionPayload]] =
    repository.get(vrn)

  def get(vrn: Vrn, period: Period): Future[Option[CorrectionPayload]] =
    repository.get(vrn, period)

  def getByCorrectionPeriod(vrn: Vrn, period: Period): Future[Seq[CorrectionPayload]] =
    repository.getByCorrectionPeriod(vrn, period)
}
