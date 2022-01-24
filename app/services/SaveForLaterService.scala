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

package services

import models.corrections.CorrectionPayload
import models.requests.{SaveForLaterRequest, VatReturnRequest, VatReturnWithCorrectionRequest}
import models.{PaymentReference, Period, ReturnReference, SavedUserAnswers, VatReturn}
import repositories.{SaveForLaterRepository, VatReturnRepository}
import uk.gov.hmrc.domain.Vrn

import java.time.{Clock, Instant}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SaveForLaterService @Inject()(
                                  repository: SaveForLaterRepository,
                                  clock: Clock
                                )
                                   (implicit ec: ExecutionContext) {

  def saveAnswers(request: SaveForLaterRequest): Future[SavedUserAnswers] = {
    val answers = SavedUserAnswers(
      vrn = request.vrn,
      period = request.period,
      data = request.data,
      lastUpdated = Instant.now(clock)
    )
    repository.set(answers)
  }

  def get(vrn: Vrn) :  Future[Seq[SavedUserAnswers]] =
    repository.get(vrn)

  def delete(vrn: Vrn, period: Period): Future[Boolean] =
    repository.clear(vrn, period)

}
