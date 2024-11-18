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

package models.etmp

import play.api.libs.json.{Json, OFormat}

final case class EtmpObligationsQueryParameters(
                                               fromDate: String,
                                               toDate: String,
                                               status: Option[String]
                                               ) {

  import EtmpObligationsQueryParameters._

  private val statusQueryParams: Seq[(String, String)] = status match {
    case Some(s) => Seq(statusKey -> s)
    case _ => Seq.empty
  }

  val toSeqQueryParams: Seq[(String, String)] = Seq(
    dateFromKey -> fromDate,
    dateToKey -> toDate
  ) ++
    statusQueryParams
}

object EtmpObligationsQueryParameters {

  val dateFromKey: String = "from"
  val dateToKey: String = "to"
  val statusKey: String = "status"

  implicit val format: OFormat[EtmpObligationsQueryParameters] = Json.format[EtmpObligationsQueryParameters]
}
