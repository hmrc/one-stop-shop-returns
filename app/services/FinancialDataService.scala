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

import connectors.FinancialDataConnector
import logging.Logging
import models.des.DesException
import models.financialdata._
import models.{Period, Quarter, StandardPeriod}
import uk.gov.hmrc.domain.Vrn

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class FinancialDataService @Inject()(
                                      financialDataConnector: FinancialDataConnector,
                                      vatReturnService: VatReturnService,
                                      vatReturnSalesService: VatReturnSalesService,
                                      periodService: PeriodService,
                                      correctionService: CorrectionService
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

  def getVatReturnWithFinancialData(vrn: Vrn, commencementDate: LocalDate): Future[Seq[VatReturnWithFinancialData]] = {
    (for {
      vatReturns <- vatReturnService.get(vrn)
      maybeFinancialDataResponse <- getFinancialData(vrn, commencementDate).recover {
        case e: Exception =>
          logger.error(s"Error while getting vat return with financial data: ${e.getMessage}", e)
          None
      }
    } yield {
      Future.sequence(vatReturns.map { vatReturn =>
        val charge = maybeFinancialDataResponse.flatMap {
          _.financialTransactions.flatMap {
            transactions => getChargeForPeriod(vatReturn.period, transactions)
          }
        }

        correctionService.get(vatReturn.vrn, vatReturn.period).map {
          maybeCorrectionPayload =>
          VatReturnWithFinancialData(
            vatReturn,
            charge,
            charge.map(c => c.outstandingAmount)
            .getOrElse(vatReturnSalesService.getTotalVatOnSalesAfterCorrection(vatReturn, maybeCorrectionPayload)),
            maybeCorrectionPayload)
        }
      })
    }).flatten
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
                                    vatReturnsWithFinancialData: Seq[VatReturnWithFinancialData]
                                  ): Seq[VatReturnWithFinancialData] = {
    vatReturnsWithFinancialData.filter {
      vatReturnWithFinancialData =>

        val hasChargeWithOutstanding =
          vatReturnWithFinancialData.charge.exists(_.outstandingAmount > 0)

        val expectingCharge =
          vatReturnWithFinancialData.charge.isEmpty &&
            vatReturnSalesService.getTotalVatOnSalesAfterCorrection(
              vatReturnWithFinancialData.vatReturn,
              vatReturnWithFinancialData.corrections
            ) > 0

        hasChargeWithOutstanding || expectingCharge
    }
  }
}
