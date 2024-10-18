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

package models

import play.api.libs.json._
import uk.gov.hmrc.domain.Vrn

import java.time.{Instant, LocalDate}

case class VatReturn(
                      vrn: Vrn,
                      period: Period,
                      reference: ReturnReference,
                      paymentReference: PaymentReference,
                      startDate: Option[LocalDate],
                      endDate: Option[LocalDate],
                      salesFromNi: List[SalesToCountry],
                      salesFromEu: List[SalesFromEuCountry],
                      submissionReceived: Instant,
                      lastUpdated: Instant
                    )

object VatReturn {

  implicit val format: OFormat[VatReturn] = Json.format[VatReturn]
}

trait EncryptedVatReturn

object EncryptedVatReturn {

  def reads: Reads[EncryptedVatReturn] =
    NewEncryptedVatReturn.format.widen[EncryptedVatReturn] orElse
      LegacyEncryptedVatReturn.format.widen[EncryptedVatReturn]

  def writes: Writes[EncryptedVatReturn] = Writes {
    case n: NewEncryptedVatReturn => Json.toJson(n)(NewEncryptedVatReturn.format)
    case l: LegacyEncryptedVatReturn => Json.toJson(l)(LegacyEncryptedVatReturn.format)
  }

  implicit val format: Format[EncryptedVatReturn] = Format(reads, writes)
}

case class NewEncryptedVatReturn(
                               vrn: Vrn,
                               period: Period,
                               reference: ReturnReference,
                               paymentReference: PaymentReference,
                               startDate: Option[LocalDate],
                               endDate: Option[LocalDate],
                               salesFromNi: List[EncryptedSalesToCountry],
                               salesFromEu: List[EncryptedSalesFromEuCountry],
                               submissionReceived: Instant,
                               lastUpdated: Instant
                             ) extends EncryptedVatReturn

object NewEncryptedVatReturn {

  implicit val format: OFormat[NewEncryptedVatReturn] = Json.format[NewEncryptedVatReturn]
}

case class LegacyEncryptedVatReturn(
                               vrn: Vrn,
                               period: Period,
                               reference: ReturnReference,
                               paymentReference: PaymentReference,
                               startDate: Option[LocalDate],
                               endDate: Option[LocalDate],
                               salesFromNi: List[EncryptedSalesToCountry],
                               salesFromEu: List[EncryptedSalesFromEuCountry],
                               submissionReceived: Instant,
                               lastUpdated: Instant
                             ) extends EncryptedVatReturn

object LegacyEncryptedVatReturn {

  implicit val format: OFormat[LegacyEncryptedVatReturn] = Json.format[LegacyEncryptedVatReturn]
}