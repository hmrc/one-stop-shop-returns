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

import config.AppConfig
import connectors.{CoreVatReturnConnector, RegistrationConnector}
import logging.Logging
import models.corrections.CorrectionPayload
import models.Period
import uk.gov.hmrc.domain.Vrn
import utils.ObfuscationUtils.obfuscateVrn

import java.time.{Clock, Instant}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait HistoricalReturnSubmitService

class HistoricalReturnSubmitServiceImpl @Inject()(
                                               vatReturnService: VatReturnService,
                                               correctionService: CorrectionService,
                                               coreVatReturnService: CoreVatReturnService,
                                               coreVatReturnConnector: CoreVatReturnConnector,
                                               registrationConnector: RegistrationConnector,
                                               appConfig: AppConfig,
                                               clock: Clock
                                             )
                                             (implicit ec: ExecutionContext) extends HistoricalReturnSubmitService with Logging {

  val startTransfer: Future[Any] = transfer()

  def transfer(): Future[Any] = {
    if(appConfig.coreVatReturnsEnabled) {
      logger.debug("Starting to process all historical returns to send to core")

      vatReturnService.get().flatMap { allReturns =>
        correctionService.get().flatMap { allCorrections =>
          val amountOfReturns = allReturns.size
          val amountOfCorrections = allCorrections.size
          logger.info(s"There are $amountOfReturns returns and $amountOfCorrections corrections. Starting processing...")
          val allReturnsToCore = allReturns.map { singleReturn =>
            logger.info(s"Converting return for ${obfuscateVrn(singleReturn.vrn)} and ${singleReturn.period}")
            val correctionPayload = filterCorrectionByVrnAndPeriod(allCorrections, singleReturn.vrn, singleReturn.period)
            logger.info(s"Following correction for ${obfuscateVrn(singleReturn.vrn)} and ${singleReturn.period}: ${correctionPayload.corrections.nonEmpty}")

            registrationConnector.getRegistration(singleReturn.vrn).flatMap {
              case Some(registration) =>
                coreVatReturnService.toCore(singleReturn, correctionPayload, registration).flatMap { coreVatReturn =>
                  coreVatReturnConnector.submit(coreVatReturn).map {
                    case Right(value) =>
                      logger.info(s"Successfully sent return to core for ${obfuscateVrn(singleReturn.vrn)} and ${singleReturn.period}")
                      Right(value)
                    case Left(error) =>
                      logger.error(s"Failure with sending return to core for ${obfuscateVrn(singleReturn.vrn)} and ${singleReturn.period} $error")
                      Left(error)
                  }
                }
              case _ =>
                val errorMessage = s"No registration found for VRN ${obfuscateVrn(singleReturn.vrn)}"
                logger.error(errorMessage)
                Future.failed(new Exception(errorMessage))
            }
          }

          def lift[T](futures: Seq[Future[T]]) =
            futures.map(_.map {
              Success(_)
            }.recover { case t => Failure(t) })

          def waitAll[T](futures: Seq[Future[T]]) =
            Future.sequence(lift(futures))

          waitAll(allReturnsToCore).map { allResults =>
            val successfulSends = allResults.filter(_.isSuccess)
            val errorSends = allResults.filter(_.isFailure)
            logger.warn(s"There was a total of ${allResults.size} returns sent, of which ${errorSends.size} errored and ${successfulSends.size} were successful")
            allResults
          }

        }
      }.recoverWith {
        case e: Exception =>
          logger.error(s"Error occurred while getting historical data ${e.getMessage}", e)
          Future.unit
      }
    } else {
      logger.info("Skipping historical data transfer due to toggle off")
      Future.unit
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
