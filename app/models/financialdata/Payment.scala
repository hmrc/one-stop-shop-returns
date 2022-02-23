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

package models.financialdata

import models.Period
import play.api.libs.json.{Format, Json}

import java.time.LocalDate

case class Payment(period: Period,
                   amountOwed: Long,
                   dateDue: LocalDate,
                   paymentStatus: Option[PaymentStatus]
                  )

object Payment {
  implicit val formatPayment: Format[Payment] = Json.format[Payment]

  def fromVatReturnWithFinancialData(vatReturnWithFinancialData: VatReturnWithFinancialData): Payment = {
    val paymentStatus = vatReturnWithFinancialData.charge
      .map(status => if (status.outstandingAmount == status.originalAmount) PaymentStatus.Unpaid else PaymentStatus.Partial)

    Payment(vatReturnWithFinancialData.vatReturn.period, vatReturnWithFinancialData.vatOwed.getOrElse(0),
      vatReturnWithFinancialData.vatReturn.period.paymentDeadline,
      paymentStatus)
  }
}