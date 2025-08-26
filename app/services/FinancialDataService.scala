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

import config.AppConfig
import connectors.{FinancialDataConnector, VatReturnConnector}
import logging.Logging
import models.des.DesException
import models.etmp.{EtmpObligationsFulfilmentStatus, EtmpObligationsQueryParameters}
import models.financialdata.*
import models.{Period, Quarter, StandardPeriod}
import uk.gov.hmrc.domain.Vrn
import utils.Formatters.etmpDateFormatter

import java.time.{Clock, LocalDate}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class FinancialDataService @Inject()(
                                      financialDataConnector: FinancialDataConnector,
                                      vatReturnService: VatReturnService,
                                      vatReturnSalesService: VatReturnSalesService,
                                      vatReturnConnector: VatReturnConnector,
                                      periodService: PeriodService,
                                      correctionService: CorrectionService,
                                      clock: Clock,
                                      config: AppConfig
                                    )(implicit ec: ExecutionContext) extends Logging {

  def getCharge(vrn: Vrn, period: Period): Future[Option[Charge]] = {
    getFinancialDataForDateRange(vrn, period.firstDay, period.lastDay).map {
      _.flatMap {
        _.financialTransactions.flatMap {
          transactions =>
            getChargeForPeriod(period, transactions)
        }
      }
    }
  }

  private def getFulfilledPeriods(vrn: Vrn, commencementDate: LocalDate): Future[Seq[Period]] = {

    if (config.strategicReturnApiEnabled) {
      
      val now = LocalDate.now(clock)

      val (fromDate, toDate) = if (now.isBefore(commencementDate)) {
        (now, periodService.getRunningPeriod(commencementDate).lastDay)
      } else {
        (commencementDate, periodService.getRunningPeriod(now).lastDay)
      }

      val queryParameters = EtmpObligationsQueryParameters(
        fromDate = fromDate.format(etmpDateFormatter),
        toDate = toDate.format(etmpDateFormatter),
        status = None
      )

      vatReturnConnector.getObligations(vrn.vrn, queryParameters).map {
        case Right(obligations) =>

          obligations.obligations.flatMap { obligation =>
            obligation.obligationDetails
              .filter(_.status == EtmpObligationsFulfilmentStatus.Fulfilled)
              .map(detail => Period.fromKey(detail.periodKey))
          }
        case Left(errorResponse) =>
          val message = s"Failed to retrieve obligations for VRN $vrn, error: ${errorResponse.body}"
          val exception = new Exception(message)
          logger.error(exception.getMessage, exception)
          throw exception
      }

    } else {
      vatReturnService.get(vrn).map(_.map(_.period))
    }


  }

  def getVatReturnWithFinancialData(vrn: Vrn, commencementDate: LocalDate): Future[Seq[PeriodWithFinancialData]] = {
    for {
      fulfilledPeriods <- getFulfilledPeriods(vrn, commencementDate)
      maybeFinancialDataResponse <- getFinancialData(vrn, commencementDate).recover {
        case e: Exception =>
          logger.error(s"Error while getting vat return with financial data: ${e.getMessage}", e)
          None
      }
      chargesWithFulfilledPeriods <- getChargesForFulfilledPeriods(vrn, fulfilledPeriods, maybeFinancialDataResponse)
    } yield chargesWithFulfilledPeriods
  }

  private def getChargesForFulfilledPeriods(
                                             vrn: Vrn,
                                             fulfilledPeriods: Seq[Period],
                                             maybeFinancialDataResponse: Option[FinancialData]
                                           ): Future[Seq[PeriodWithFinancialData]] = {
    val allCharges = fulfilledPeriods.map { period =>
      val maybeCharge = maybeFinancialDataResponse.flatMap {
        _.financialTransactions.flatMap { transactions =>
          getChargeForPeriod(period, transactions)
        }
      }

      maybeCharge match {
        case Some(charge) =>
          Future.successful(
            PeriodWithFinancialData(
              period = period,
              charge = Some(charge),
              vatOwed = charge.outstandingAmount,
              expectedCharge = true
            )
          )
        case None =>
          if(config.strategicReturnApiEnabled) {
            vatReturnConnector.get(vrn, period).map {
              case Right(etmpVatReturn) =>
                val vatOwed = etmpVatReturn.totalVATAmountDueForAllMSGBP
                PeriodWithFinancialData(
                  period = period,
                  charge = None,
                  vatOwed = vatOwed,
                  expectedCharge = vatOwed > 0
                )
              case Left(error) =>
                val message = s"There was an error with getting vat return during a missing charge ${error.body}"
                val exception = Exception(message)
                logger.error(exception.getMessage, exception)
                throw exception
            }
          } else {
            vatReturnService.get(vrn, period).flatMap {
              case Some(vatReturn) =>
                correctionService.get(vrn, period).map { maybeCorrectionPayload =>
                  val vatOwed = vatReturnSalesService.getTotalVatOnSalesAfterCorrection(vatReturn, maybeCorrectionPayload)
                  PeriodWithFinancialData(
                    period = period,
                    charge = None,
                    vatOwed = vatOwed,
                    expectedCharge = vatOwed > 0
                  )
                }
              case None =>
                val message = s"VAT Return not found for VRN $vrn and period $period"
                val exception = Exception(message)
                logger.error(exception.getMessage, exception)
                throw exception
            }
          }
      }
    }
    
    Future.sequence(allCharges)
  }

  private def getChargeForPeriod(period: Period, transactions: Seq[FinancialTransaction]): Option[Charge] = {
    val transactionsForPeriod = transactions.filter(t => t.taxPeriodFrom.contains(period.firstDay))
    if (transactionsForPeriod.nonEmpty) {
      Some(
        Charge(
          period,
          originalAmount = transactionsForPeriod.map(_.originalAmount.getOrElse(BigDecimal(0))).sum,
          outstandingAmount = transactionsForPeriod.map(_.outstandingAmount.getOrElse(BigDecimal(0))).sum,
          clearedAmount = transactionsForPeriod.map(_.clearedAmount.getOrElse(BigDecimal(0))).sum
        )
      )
    } else {
      None
    }
  }

  def getFinancialData(vrn: Vrn, fromDate: LocalDate): Future[Option[FinancialData]] = {
    val financialDatas: Future[Seq[FinancialData]] =
      Future.sequence(
        periodService.getPeriodYears(fromDate).map {
          taxYear => getFinancialDataForDateRange(vrn, taxYear.startOfYear, taxYear.endOfYear)
        }
      ).map(_.flatten)

    financialDatas.map {
      case firstFinancialData :: Nil => Some(firstFinancialData)
      case firstFinancialData :: rest =>
        val otherFinancialTransactions = rest.flatMap(_.financialTransactions).flatten

        val allTransactions =
          firstFinancialData.financialTransactions.getOrElse(Nil) ++ otherFinancialTransactions

        val maybeAllTransactions = if (allTransactions.isEmpty) None else Some(allTransactions)

        Some(firstFinancialData.copy(financialTransactions = maybeAllTransactions))
      case Nil => None
    }
  }

  private def getFinancialDataForDateRange(vrn: Vrn, fromDate: LocalDate, toDate: LocalDate): Future[Option[FinancialData]] =
    financialDataConnector.getFinancialData(
      vrn,
      FinancialDataQueryParameters(
        fromDate = Some(fromDate),
        toDate = Some(toDate)
      )
    ).flatMap {
      case Right(value) => Future.successful(value)
      case Left(e) => Future.failed(DesException(s"An error occurred while getting financial Data: ${e.body}"))
    }

  def getOutstandingAmounts(vrn: Vrn): Future[Seq[PeriodWithOutstandingAmount]] = {
    for {
      periodsWithOutstandingAmounts <-
        financialDataConnector.getFinancialData(
          vrn,
          FinancialDataQueryParameters(
            fromDate = None,
            toDate = None,
            onlyOpenItems = Some(true)
          )
        ).flatMap {
          case Right(maybeFinancialDataResponse) =>
            maybeFinancialDataResponse match {
              case Some(financialData) =>
                Future.successful(
                  financialData.financialTransactions.getOrElse(Seq.empty)
                    .filter(transaction => transaction.outstandingAmount.getOrElse(BigDecimal(0)) > BigDecimal(0))
                    .groupBy(transaction => transaction.taxPeriodFrom)
                    .map {
                      case (Some(periodStart), transactions: Seq[FinancialTransaction]) =>
                        PeriodWithOutstandingAmount(
                          StandardPeriod(periodStart.getYear, Quarter.quarterFromStartMonth(periodStart.getMonth)),
                          transactions.map(_.outstandingAmount.getOrElse(BigDecimal(0))).sum
                        )
                      case (None, _) => throw DesException("An error occurred while getting financial Data - periodStart was None")
                    }.toSeq
                    .sortBy(_.period.toString).reverse
                )
              case None => Future.successful(Seq.empty)
            }
          case Left(e) =>
            Future.failed(DesException(s"An error occurred while getting financial Data: ${e.body}"))
        }
    } yield periodsWithOutstandingAmounts
  }

  def filterIfPaymentIsOutstanding(
                                    periodsWithFinancialData: Seq[PeriodWithFinancialData]
                                  ): Seq[PeriodWithFinancialData] = {
    periodsWithFinancialData.filter {
      vatReturnWithFinancialData =>

        val hasChargeWithOutstanding =
          vatReturnWithFinancialData.charge.exists(_.outstandingAmount > 0)

        val expectingCharge =
          vatReturnWithFinancialData.charge.isEmpty && vatReturnWithFinancialData.expectedCharge

        hasChargeWithOutstanding || expectingCharge
    }
  }

  def filterIfPaymentIsComplete(
                                    periodsWithFinancialData: Seq[PeriodWithFinancialData]
                                  ): Seq[PeriodWithFinancialData] = {
    periodsWithFinancialData.filter { vatReturnWithFinancialData =>

      vatReturnWithFinancialData.vatOwed == 0

    }
  }
}
