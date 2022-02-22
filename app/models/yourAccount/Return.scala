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

package models.yourAccount

import models.Period
import play.api.libs.json.{Json, OFormat}

import java.time.LocalDate

case class Return (
                  period: Period,
                  firstDay: LocalDate,
                  lastDay: LocalDate,
                  dueDate: LocalDate
                  )

case object Return {
  implicit val format: OFormat[Return] = Json.format[Return]

  def fromPeriod(period: Period) = Return(period, period.firstDay, period.lastDay, period.paymentDeadline)
}