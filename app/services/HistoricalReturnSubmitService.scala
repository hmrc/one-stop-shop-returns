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
import models.core.{CorePeriod, CoreVatReturn, EisErrorResponse}
import models.corrections.CorrectionPayload
import models.{Period, VatReturn}
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

  private def filterCorrectionByVrnAndPeriod(allCorrections: Seq[CorrectionPayload], vrn: Vrn, period: Period): CorrectionPayload = {
    allCorrections.find(correctionPayload => correctionPayload.vrn == vrn && correctionPayload.period == period) match {
      case Some(v) => v
      case _ =>
        val now = Instant.now(clock)
        CorrectionPayload(vrn, period, List.empty, now, now)
    }
  }

  def submitReturnsByQuarter(returnsByQuarter: Seq[Seq[CoreVatReturn]], completedPeriods: Int = 0): Future[Either[EisErrorResponse, Unit]] = {

     def submitSequentially(returns: Seq[CoreVatReturn], completedReturns: Int = 0): Future[Either[EisErrorResponse, Unit]] = {
       if(returns.isEmpty) {
         Future.successful(Right())
       } else {
         coreVatReturnConnector.submit(returns.head).flatMap {
             case Right(_) => {
               logger.info(s"Successfully sent return to core for ${obfuscateVrn(returns.head.vatReturnReferenceNumber)} and ${returns.head.period}")
               submitSequentially(returns.tail, completedReturns + 1)
             }
             case Left(t) => {
               logger.error(s"Failure sending return to core for ${obfuscateVrn(returns.head.vatReturnReferenceNumber)} and ${returns.head.period}: ${t.errorDetail.errorMessage}")
               logger.error(s"$completedReturns successful, ${returns.length} unsuccessful submissions for ${returns.head.period}")
               Future.successful(Left(t))
             }
         }
       }
    }

    if(returnsByQuarter.isEmpty) {
      logger.info(s"No periods to submit, successfully submitted $completedPeriods periods")
      Future.successful(Right())
    } else  {
      logger.info(s"Submitting ${returnsByQuarter.head.length} returns for quarter ${returnsByQuarter.head.head.period}")
      submitSequentially(returnsByQuarter.head).flatMap {
        case Right(_) => {
          logger.info(s"Successfully sent ${returnsByQuarter.head.length} returns to core for ${returnsByQuarter.head.head.period}")
          submitReturnsByQuarter(returnsByQuarter.tail, completedPeriods + 1)
        }
        case Left(t) => {
          logger.error(s"Submissions failed for ${returnsByQuarter.head.head.period}")
          logger.error(s"$completedPeriods periods successful, ${returnsByQuarter.length} unsuccessful")
          Future.successful(Left(t))
        }
      }
    }
  }

  def transfer(): Future[Any] = {

    if(appConfig.historicCoreVatReturnsEnabled) {

      logger.debug("Starting to process all historical returns to send to core")

      val vatReturnsAndCorrections: Future[Seq[(VatReturn, CorrectionPayload)]] =
        (
          for {
            allReturns <- vatReturnService.get()
            allCorrections <- correctionService.get()
          } yield {
            val amountOfReturns = allReturns.size
            val amountOfCorrections = allCorrections.size

            logger.info(s"There are $amountOfReturns returns and $amountOfCorrections corrections. Starting processing...")

            allReturns.map { singleReturn =>
              val correctionPayload = filterCorrectionByVrnAndPeriod(allCorrections, singleReturn.vrn, singleReturn.period)
              logger.info(s"Retrieved return for ${obfuscateVrn(singleReturn.vrn)} and ${singleReturn.period}, with ${correctionPayload.corrections.length} corrections")
              (singleReturn, correctionPayload)
            }
          }
       ).recoverWith {
          case e: Exception =>
            val errorMessage = s"Error occurred while getting historical data: ${e.getMessage}"
            logger.error(errorMessage)
            Future.failed(e)
      }

      logger.debug("Retrieving registrations and converting to returns to core format")

      val coreReturns: Future[Seq[CoreVatReturn]] = vatReturnsAndCorrections.flatMap { returnsAndCorrections =>

        val convertedReturnsAndCorrections: Seq[Future[CoreVatReturn]] = returnsAndCorrections.map { singleReturnAndCorrection =>
          registrationConnector.getRegistration(singleReturnAndCorrection._1.vrn).flatMap {
              case Some(r) =>
                coreVatReturnService.toCore(singleReturnAndCorrection._1, singleReturnAndCorrection._2, r)
              case _ =>
                val errorMessage = s"No registration found for VRN ${obfuscateVrn(singleReturnAndCorrection._1.vrn)}"
                logger.error(errorMessage)
                Future.failed(new Exception(errorMessage))
            }
        }

        Future.sequence[CoreVatReturn, Seq](convertedReturnsAndCorrections)
      }

      val orderedGroupedReturns: Future[Seq[Seq[CoreVatReturn]]] = for {
        returnsGroupedByPeriod <- coreReturns.map(_.groupBy(_.period))
      } yield {
        val periods = returnsGroupedByPeriod.keys.toList.sortBy{ case CorePeriod(year, quarter) => (year, quarter) }
        periods.map(period => returnsGroupedByPeriod.getOrElse(period, Seq.empty))
      }

      logger.debug("Submitting returns to core")

      (
        for {
          returns <- orderedGroupedReturns
          submissionsResult <- submitReturnsByQuarter(returns)
        } yield {
          submissionsResult match {
            case Right(_) => Success()
            case Left(t) => {
              logger.error(s"Error while submitting vat returns: ${t.errorDetail.errorMessage}")
              Failure(t.errorDetail.asException)
            }
          }
        }
      ).recover {
        case t =>
          logger.error(t.getMessage)
          Failure(t)
      }

    } else {
      logger.info("Skipping historical data transfer due to toggle off")
      Future.unit
    }
  }
}
