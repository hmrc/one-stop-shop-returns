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

package services

import models.{InsertResult, Period, ReturnReference, VatReturn}
import models.requests.VatReturnRequest
import repositories.VatReturnRepository
import uk.gov.hmrc.domain.Vrn

import java.time.{Clock, Instant}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class VatReturnService @Inject()(
                                  repository: VatReturnRepository,
                                  clock: Clock
                                )
                                (implicit ec: ExecutionContext) {

  def createVatReturn(request: VatReturnRequest): Future[InsertResult] = {
    val vatReturn = VatReturn(
      vrn                = request.vrn,
      period             = request.period,
      reference          = ReturnReference(request.vrn, request.period),
      startDate          = request.startDate,
      endDate            = request.endDate,
      salesFromNi        = request.salesFromNi,
      salesFromEu        = request.salesFromEu,
      submissionReceived = Instant.now(clock),
      lastUpdated        = Instant.now(clock)
    )

    repository.insert(vatReturn)
  }

  def get(vrn: Vrn): Future[Seq[VatReturn]] =
    repository.get(vrn)
}