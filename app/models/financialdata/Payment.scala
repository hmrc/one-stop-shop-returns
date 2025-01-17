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

package models.financialdata

import models.Period
import models.Period.isThreeYearsOld
import models.exclusions.ExcludedTrader
import play.api.libs.json.{Format, Json}

import java.time.{Clock, LocalDate}

case class Payment(period: Period,
                   amountOwed: BigDecimal,
                   dateDue: LocalDate,
                   paymentStatus: PaymentStatus)

object Payment {
  implicit val formatPayment: Format[Payment] = Json.format[Payment]

  private def isPeriodExcluded(maybeExclusion: Option[ExcludedTrader], period: Period, clock: Clock) = {
    maybeExclusion match {
      case Some(_) if isThreeYearsOld(period.paymentDeadline, clock) => true
      case _ => false
    }
  }

  def fromVatReturnWithFinancialData(vatReturnWithFinancialData: PeriodWithFinancialData,
                                     maybeExclusion: Option[ExcludedTrader],
                                     clock: Clock): Payment = {

    val period = vatReturnWithFinancialData.period

    val paymentStatus: PaymentStatus =
      vatReturnWithFinancialData.charge
        .fold {
          if(maybeExclusion.exists(_.isExcludedNotReversed) && isPeriodExcluded(maybeExclusion, period, clock)) {
            PaymentStatus.Excluded
          } else {
            PaymentStatus.Unknown
          }
        } { paymentCharge =>

          if(maybeExclusion.exists(_.isExcludedNotReversed) && isPeriodExcluded(maybeExclusion, period, clock)) {
            PaymentStatus.Excluded
          } else if(paymentCharge.outstandingAmount == paymentCharge.originalAmount) {
              PaymentStatus.Unpaid
            } else {
            PaymentStatus.Partial
          }
        }

    Payment(
      vatReturnWithFinancialData.period,
      vatReturnWithFinancialData.vatOwed,
      vatReturnWithFinancialData.period.paymentDeadline,
      paymentStatus
    )
  }
}
