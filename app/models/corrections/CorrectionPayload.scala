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

package models.corrections

import models.Period
import play.api.libs.json._
import uk.gov.hmrc.domain.Vrn

import java.time.Instant

case class CorrectionPayload(
                              vrn: Vrn,
                              period: Period,
                              corrections: List[PeriodWithCorrections],
                              submissionReceived: Instant,
                              lastUpdated: Instant
                            )

object CorrectionPayload {

  implicit val format: OFormat[CorrectionPayload] = Json.format[CorrectionPayload]

}

trait EncryptedCorrectionPayload

object EncryptedCorrectionPayload {

  def reads: Reads[EncryptedCorrectionPayload] =
    NewEncryptedCorrectionPayload.format.widen[EncryptedCorrectionPayload] orElse
      LegacyEncryptedCorrectionPayload.format.widen[EncryptedCorrectionPayload]

  def writes: Writes[EncryptedCorrectionPayload] = Writes {
    case n: NewEncryptedCorrectionPayload => Json.toJson(n)(NewEncryptedCorrectionPayload.format)
    case l: LegacyEncryptedCorrectionPayload => Json.toJson(l)(LegacyEncryptedCorrectionPayload.format)
  }

  implicit val format: Format[EncryptedCorrectionPayload] = Format(reads, writes)

}

case class NewEncryptedCorrectionPayload(
                                       vrn: Vrn,
                                       period: Period,
                                       corrections: List[EncryptedPeriodWithCorrections],
                                       submissionReceived: Instant,
                                       lastUpdated: Instant
                                     ) extends EncryptedCorrectionPayload

object NewEncryptedCorrectionPayload {

  implicit val format: OFormat[NewEncryptedCorrectionPayload] = Json.format[NewEncryptedCorrectionPayload]

}

case class LegacyEncryptedCorrectionPayload(
                                       vrn: Vrn,
                                       period: Period,
                                       corrections: List[EncryptedPeriodWithCorrections],
                                       submissionReceived: Instant,
                                       lastUpdated: Instant
                                     ) extends EncryptedCorrectionPayload

object LegacyEncryptedCorrectionPayload {

  implicit val format: OFormat[LegacyEncryptedCorrectionPayload] = Json.format[LegacyEncryptedCorrectionPayload]

}