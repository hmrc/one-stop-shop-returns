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

import connectors.CoreVatReturnConnector
import logging.Logging
import models.corrections.CorrectionPayload
import models.Period
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{Clock, Instant}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class HistoricalReturnSubmitService @Inject()(
                                               vatReturnService: VatReturnService,
                                               correctionService: CorrectionService,
                                               coreVatReturnService: CoreVatReturnService,
                                               coreVatReturnConnector: CoreVatReturnConnector,
                                               clock: Clock
                                             )
                                             (implicit ec: ExecutionContext) extends Logging {

  def transfer()(implicit hc: HeaderCarrier): Future[Any] = {
    logger.debug("Starting to process all historical returns to send to core")

    vatReturnService.get().flatMap{ allReturns =>
      correctionService.get().flatMap { allCorrections =>
        val amountOfReturns = allReturns.size
        val amountOfCorrections = allCorrections.size
        logger.info(s"There are $amountOfReturns returns and $amountOfCorrections corrections. Starting processing...")
        val allReturnsToCore = allReturns.map { singleReturn =>
          val correctionPayload = filterCorrectionByVrnAndPeriod(allCorrections, singleReturn.vrn, singleReturn.period)
          coreVatReturnService.toCore(singleReturn, correctionPayload).flatMap { coreVatReturn =>
            coreVatReturnConnector.submit(coreVatReturn)
          }
        }

        Future.sequence(allReturnsToCore)
      }
    }

  }


  private def filterCorrectionByVrnAndPeriod(allCorrections: Seq[CorrectionPayload], vrn: Vrn, period: Period): CorrectionPayload = {
    allCorrections.find(correctionPayload => correctionPayload.vrn == vrn && correctionPayload.period == period) match {
      case Some(v) => v
      case _ =>
        val now = Instant.now(clock)
        CorrectionPayload(vrn, period, List.empty, now, now)
    }
  }

}
