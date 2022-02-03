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

package models.core

import models.{Country, Period, SalesDetails, SalesFromEuCountry, SalesToCountry, VatReturn}
import models.corrections.{CorrectionPayload, PeriodWithCorrections}
import play.api.libs.json.{__, Json, OFormat}

import java.time.{Instant, LocalDate}
import java.util.UUID

case class CoreTraderId(vatNumber: String, issuedBy: String)

object CoreTraderId {
  implicit val format: OFormat[CoreTraderId] = Json.format[CoreTraderId]
}

case class CorePeriod(year: Int, quarter: Int)

object CorePeriod {
  implicit val format: OFormat[CorePeriod] = Json.format[CorePeriod]
}

case class CoreSupply(
                       supplyType: String,
                       vatRate: BigDecimal,
                       vatRateType: String,
                       taxableAmountGBP: BigDecimal,
                       vatAmountGBP: BigDecimal
                     )

object CoreSupply {
  implicit val format: OFormat[CoreSupply] = Json.format[CoreSupply]
}

case class CoreEuTraderId(vatIdNumber: String, issuedBy: String)

object CoreEuTraderId {
  implicit val format: OFormat[CoreEuTraderId] = Json.format[CoreEuTraderId]
}

case class CoreMsestSupply(
                            countryCode: Option[String],
                            euTraderId: Option[CoreEuTraderId],
                            supplies: List[CoreSupply]
                          )

object CoreMsestSupply {
  implicit val format: OFormat[CoreMsestSupply] = Json.format[CoreMsestSupply]
}


case class CoreCorrection(
                           period: CorePeriod,
                           totalVatAmountCorrectionGBP: BigDecimal
                         )

object CoreCorrection {
  implicit val format: OFormat[CoreCorrection] = Json.format[CoreCorrection]
}

case class CoreMsconSupply(
                            msconCountryCode: String,
                            balanceOfVatDueGBP: BigDecimal,
                            grandTotalMsidGoodsGBP: BigDecimal,
                            grandTotalMsestGoodsGBP: BigDecimal,
                            correctionsTotalGBP: BigDecimal,
                            msidSupplies: List[CoreSupply],
                            msestSupplies: List[CoreMsestSupply],
                            corrections: List[CoreCorrection]
                          )

object CoreMsconSupply {
  implicit val format: OFormat[CoreMsconSupply] = Json.format[CoreMsconSupply]
}

case class CoreVatReturn(
                          vatReturnReferenceNumber: String,
                          version: String,
                          traderId: CoreTraderId,
                          period: CorePeriod,
                          startDate: LocalDate,
                          endDate: LocalDate,
                          submissionDateTime: Instant,
                          totalAmountVatDueGBP: BigDecimal,
                          msconSupplies: List[CoreMsconSupply]
                        )

object CoreVatReturn {
  implicit val format: OFormat[CoreVatReturn] = Json.format[CoreVatReturn]

}

case class CoreErrorResponse(
                              timestamp: Instant,
                              transactionId: Option[UUID],
                              error: String,
                              errorMessage: String
                            ) {
  val asException: Exception = new Exception(s"$timestamp $transactionId $error $errorMessage")
}

object CoreErrorResponse {
  implicit val format: OFormat[CoreErrorResponse] = Json.format[CoreErrorResponse]
  val REGISTRATION_NOT_FOUND = "OSS_009"
}