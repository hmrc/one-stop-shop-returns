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
import play.api.libs.json.{Json, OFormat}
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

case class EncryptedCorrectionPayload(
                                       vrn: Vrn,
                                       period: Period,
                                       corrections: List[EncryptedPeriodWithCorrections],
                                       submissionReceived: Instant,
                                       lastUpdated: Instant
                                     )

object EncryptedCorrectionPayload {

  implicit val format: OFormat[EncryptedCorrectionPayload] = Json.format[EncryptedCorrectionPayload]

}