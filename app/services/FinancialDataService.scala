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

import connectors.FinancialDataConnector
import connectors.FinancialDataHttpParser.FinancialDataResponse
import logging.Logging
import models.{Period, Quarter}
import models.des.DesException
import models.financialdata._
import models.Quarter.{Q3, Q4}
import uk.gov.hmrc.domain.Vrn

import java.time.{Clock, LocalDate}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class FinancialDataService @Inject()(
                                      financialDataConnector: FinancialDataConnector,
                                      clock: Clock
                                    )(implicit ec: ExecutionContext) extends Logging {

  def getCharge(vrn: Vrn, period: Period): Future[Option[Charge]] = {
    getFinancialData(vrn, period.firstDay).map { maybeFinancialDataResponse =>
      maybeFinancialDataResponse.flatMap {
        financialDataResponse =>
          financialDataResponse.financialTransactions.map {
            transactions =>
              val transactionsForPeriod = transactions.filter(t => t.taxPeriodFrom.contains(period.firstDay))
              Charge(
                period = period,
                originalAmount = transactionsForPeriod.map(_.originalAmount.getOrElse(BigDecimal(0))).sum,
                outstandingAmount = transactionsForPeriod.map(_.outstandingAmount.getOrElse(BigDecimal(0))).sum,
                clearedAmount = transactionsForPeriod.map(_.clearedAmount.getOrElse(BigDecimal(0))).sum
              )
          }
      }
    }
  }

  def getFinancialData(vrn: Vrn, commencementDate: LocalDate): Future[Option[FinancialData]] =
    financialDataConnector.getFinancialData(vrn, FinancialDataQueryParameters(fromDate = Some(commencementDate), toDate = Some(LocalDate.now()))).flatMap {
      case Right(value) => Future.successful(value)
      case Left(e) => Future.failed(DesException(s"An error occurred while getting financial Data: ${e.body}"))
    }

  def getOutstandingAmounts(vrn: Vrn, commencementDate: LocalDate): Future[Seq[PeriodWithOutstandingAmount]] = {
    testRequests(vrn).flatMap { _ =>
      financialDataConnector.getFinancialData(vrn,
        FinancialDataQueryParameters(
          fromDate = Some(commencementDate),
          toDate = Some(LocalDate.now(clock)),
          onlyOpenItems = Some(true) // TODO check if this makes sense
        )).flatMap {
        case Right(maybeFinancialDataResponse) => maybeFinancialDataResponse match {
          case Some(financialData) =>
            Future.successful(financialData.financialTransactions.getOrElse(Seq.empty)
              .filter(transaction => transaction.outstandingAmount.getOrElse(BigDecimal(0)) > BigDecimal(0))
              .groupBy(transaction => transaction.taxPeriodFrom)
              .map {
                case (Some(periodStart), transactions: Seq[FinancialTransaction]) =>
                  PeriodWithOutstandingAmount(Period(periodStart.getYear, Quarter.quarterFromStartMonth(periodStart.getMonth)), transactions.map(_.outstandingAmount.getOrElse(BigDecimal(0))).sum)
              }.toSeq
              .sortBy(_.period.toString).reverse
            )
          case None =>
            Future.successful(Seq.empty)
        }
        case Left(e) =>
          Future.failed(DesException(s"An error occurred while getting financial Data: ${e.body}"))
      }
    }.recover {
      case e: Exception =>
        logger.info(s"The following error was thrown while getting test requests: ${e.getMessage}", e)
        throw e
    }
  }

  private def testRequests(vrn: Vrn): Future[Unit] = {

    val thisTaxYearQueryParams = FinancialDataQueryParameters(
      fromDate = Some(LocalDate.of(2020, 4, 6)),
      toDate = Some(LocalDate.of(2021, 4, 5))
    )
    val futureThisTaxYearResponse = financialDataConnector.getFinancialData(vrn, thisTaxYearQueryParams)

    val thisPeriod = Period(2021, Q4)
    val thisPeriodQueryParams = FinancialDataQueryParameters(
      fromDate = Some(thisPeriod.firstDay),
      toDate = Some(thisPeriod.lastDay)
    )
    val futureThisPeriodResponse = financialDataConnector.getFinancialData(vrn, thisPeriodQueryParams)

    val previousPeriod = Period(2021, Q3)
    val previousPeriodQueryParams = FinancialDataQueryParameters(
      fromDate = Some(previousPeriod.firstDay),
      toDate = Some(previousPeriod.lastDay)
    )
    val futurePreviousPeriodResponse = financialDataConnector.getFinancialData(vrn, previousPeriodQueryParams)

    val fiveYearQueryParam = FinancialDataQueryParameters(
      fromDate = Some(LocalDate.of(2016, 4, 6)),
      toDate = Some(LocalDate.of(2021, 4, 5))
    )
    val futureFiveYearPeriodResponse = financialDataConnector.getFinancialData(vrn, fiveYearQueryParam)


    val fiveYearTodayMiddleQueryParam = FinancialDataQueryParameters(
      fromDate = Some(LocalDate.of(2018, 4, 6)),
      toDate = Some(LocalDate.of(2023, 4, 5))
    )
    val futureFiveYearTodayMiddlePeriodResponse = financialDataConnector.getFinancialData(vrn, fiveYearTodayMiddleQueryParam)


    for {
      thisTaxYearResponse <- futureThisTaxYearResponse
      thisPeriodResponse <- futureThisPeriodResponse
      previousPeriodResponse <- futurePreviousPeriodResponse
      fiveYearPeriodResponse <- futureFiveYearPeriodResponse
      fiveYearTodayMiddlePeriodResponse <- futureFiveYearTodayMiddlePeriodResponse
    } yield {
      outputResponse("thisTaxYearResponse", thisTaxYearResponse)
      outputResponse("thisPeriodResponse", thisPeriodResponse)
      outputResponse("previousPeriodResponse", previousPeriodResponse)
      outputResponse("fiveYearPeriodResponse", fiveYearPeriodResponse)
      outputResponse("fiveYearTodayMiddlePeriodResponse", fiveYearTodayMiddlePeriodResponse)
    }

  }

  private def outputResponse(label: String, financialDataResponse: FinancialDataResponse): Unit = {
    logger.info(s"Financial Data [$label] had a response of $financialDataResponse")
  }


}
