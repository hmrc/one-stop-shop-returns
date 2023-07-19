/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.libs.json.{Json, OFormat, Reads, Writes}

import java.time.{Instant, LocalDate, LocalDateTime}
import java.util.UUID

case class CoreTraderId(vatNumber: String, issuedBy: String)

object CoreTraderId {
  implicit val format: OFormat[CoreTraderId] = Json.format[CoreTraderId]
}

case class CorePeriod(year: Int, quarter: Int) {
  override def toString: String = s"$year-Q$quarter"
}

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

trait CoreEuTraderId

  object CoreEuTraderId {

    implicit val reads: Reads[CoreEuTraderId] =
      CoreEuTraderVatId.format.widen[CoreEuTraderId] orElse
        CoreEuTraderTaxId.format.widen[CoreEuTraderId]

    implicit val writes: Writes[CoreEuTraderId] = Writes {
      case vatId: CoreEuTraderVatId => Json.toJson(vatId)(CoreEuTraderVatId.format)
      case taxId: CoreEuTraderTaxId => Json.toJson(taxId)(CoreEuTraderTaxId.format)
    }
  }

case class CoreEuTraderVatId(vatIdNumber: String, issuedBy: String) extends CoreEuTraderId

object CoreEuTraderVatId {
  implicit val format: OFormat[CoreEuTraderVatId] = Json.format[CoreEuTraderVatId]
}

case class CoreEuTraderTaxId(taxRefNumber: String, issuedBy: String) extends CoreEuTraderId

object CoreEuTraderTaxId {
  implicit val format: OFormat[CoreEuTraderTaxId] = Json.format[CoreEuTraderTaxId]
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
                          version: Instant,
                          traderId: CoreTraderId,
                          period: CorePeriod,
                          startDate: LocalDate,
                          endDate: LocalDate,
                          submissionDateTime: Instant,
                          totalAmountVatDueGBP: BigDecimal,
                          msconSupplies: List[CoreMsconSupply],
                          changeDate: Option[LocalDateTime]
                        )

object CoreVatReturn {
  implicit val format: OFormat[CoreVatReturn] = Json.format[CoreVatReturn]

}

case class CoreErrorResponse(
                              timestamp: Instant,
                              transactionId: Option[UUID],
                              errorCode: String,
                              errorMessage: String
                            ) {
  val asException: Exception = new Exception(s"$timestamp $transactionId $errorCode $errorMessage")
}

object CoreErrorResponse {
  implicit val format: OFormat[CoreErrorResponse] = Json.format[CoreErrorResponse]
  val REGISTRATION_NOT_FOUND = "OSS_009"
}

case class EisErrorResponse(
                           errorDetail: CoreErrorResponse
                           )

object EisErrorResponse {
  implicit val format: OFormat[EisErrorResponse] = Json.format[EisErrorResponse]
}