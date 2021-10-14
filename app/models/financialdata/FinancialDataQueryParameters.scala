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

package models.financialdata


import play.api.libs.json.{Format, Json}

import java.time.LocalDate

final case class FinancialDataQueryParameters(
                                               fromDate:                   Option[LocalDate] = None,
                                               toDate:                     Option[LocalDate] = None,
                                               onlyOpenItems:              Option[Boolean]   = None,
                                               includeLocks:               Option[Boolean]   = None,
                                               calculateAccruedInterest:   Option[Boolean]   = None,
                                               customerPaymentInformation: Option[Boolean]   = None
                                             ) {
  import FinancialDataQueryParameters._
  val toSeqQueryParams: Seq[(String, String)] = Seq(
    fromDate.map(dateFromKey -> _.toString),
    toDate.map(dateToKey -> _.toString),
    onlyOpenItems.map(onlyOpenItemsKey -> _.toString),
    includeLocks.map(includeLocksKey -> _.toString),
    calculateAccruedInterest.map(calculateAccruedInterestKey -> _.toString),
    customerPaymentInformation.map(customerPaymentInformationKey -> _.toString)
  ).flatten
  val hasQueryParameters: Boolean = toSeqQueryParams.nonEmpty
}

object FinancialDataQueryParameters {

  val dateFromKey: String = "dateFrom"
  val dateToKey: String = "dateTo"
  val onlyOpenItemsKey: String = "onlyOpenItems"
  val includeLocksKey: String = "includeLocks"
  val calculateAccruedInterestKey: String = "calculateAccruedInterest"
  val customerPaymentInformationKey: String = "customerPaymentInformation"

  implicit val format: Format[FinancialDataQueryParameters] = Json.format[FinancialDataQueryParameters]
}
