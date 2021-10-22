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
import models.financialdata._
import models.Period
import uk.gov.hmrc.domain.Vrn

import java.time.{Clock, LocalDate}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class FinancialDataService @Inject()(
                                      financialDataConnector: FinancialDataConnector,
                                      vatReturnService: VatReturnService,
                                      clock: Clock
                                    )(implicit ec: ExecutionContext) {

  def getCharge(vrn: Vrn, period: Period): Future[Option[Charge]] = {
    getFinancialData(vrn, period.firstDay).map { maybeFinancialDataResponse =>
      maybeFinancialDataResponse.flatMap {
        financialDataResponse =>
          financialDataResponse.financialTransactions.flatMap {
            transactions =>
              getChargeForPeriod(period, transactions)
          }
      }
    }

  }

  def getVatReturnWithFinancialData(vrn: Vrn, commencementDate: LocalDate): Future[Seq[VatReturnWithFinancialData]] = {

    val getFinancialDataResponse = financialDataConnector
      .getFinancialData(vrn,
        FinancialDataQueryParameters(
          fromDate = Some(commencementDate),
          toDate = Some(LocalDate.now(clock))
        )).map {
          case Right(v) => v
          case Left(_) => None
        }.recover {
          case _: Exception =>
            None
        }

    for {
      vatReturns <- vatReturnService.get(vrn)
      maybeFinancialDataResponse <- getFinancialDataResponse
    } yield {
      vatReturns.map { vatReturn =>
        val charge = maybeFinancialDataResponse.flatMap {
          financialDataResponse =>
            financialDataResponse.financialTransactions.flatMap {
              transactions =>
                getChargeForPeriod(vatReturn.period, transactions)
            }
        }
        VatReturnWithFinancialData(vatReturn, charge, charge.map(c => (c.outstandingAmount * 100).toLong))
      }
    }
  }

  def getFinancialData(vrn: Vrn, commencementDate: LocalDate): Future[Option[FinancialData]] =
    financialDataConnector.getFinancialData(vrn, FinancialDataQueryParameters(fromDate = Some(commencementDate), toDate = Some(LocalDate.now(clock)))).flatMap {
      case Right(value) => Future.successful(value)
      case Left(e) => Future.failed(new Exception(s"An error occurred while getting financial Data: ${e.body}"))
    }

  private def getChargeForPeriod(period: Period, transactions: Seq[FinancialTransaction]): Option[Charge] = {
    val transactionsForPeriod = transactions.filter(t => t.taxPeriodFrom.contains(period.firstDay))
    if (transactionsForPeriod.nonEmpty) {
      Some(
        Charge(
          period = period,
          originalAmount = transactionsForPeriod.map(_.originalAmount.getOrElse(BigDecimal(0))).sum,
          outstandingAmount = transactionsForPeriod.map(_.outstandingAmount.getOrElse(BigDecimal(0))).sum,
          clearedAmount = transactionsForPeriod.map(_.clearedAmount.getOrElse(BigDecimal(0))).sum
        )
      )
    } else {
      None
    }
  }

}
