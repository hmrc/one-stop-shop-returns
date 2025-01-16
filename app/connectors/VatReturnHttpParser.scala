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

package connectors

import logging.Logging
import models.etmp.EtmpVatReturn
import models.{ErrorResponse, InvalidJson, UnexpectedResponseStatus}
import play.api.http.Status.*
import play.api.libs.json.{JsError, JsSuccess}
import uk.gov.hmrc.http.{HttpReads, HttpResponse}

object VatReturnHttpParser extends Logging {

  type DisplayVatReturnResponse = Either[ErrorResponse, EtmpVatReturn]

  implicit object EtmpVatReturnReads extends HttpReads[DisplayVatReturnResponse] {
    override def read(method: String, url: String, response: HttpResponse): DisplayVatReturnResponse =
      response.status match {
        case OK =>
          response.json.validate[EtmpVatReturn] match {
            case JsSuccess(etmpVatReturn, _) =>
              Right(etmpVatReturn)
            case JsError(errors) =>
              logger.error(s"There was an error parsing the JSON response from ETMP with errors: $errors")
              Left(InvalidJson)
          }

        case status =>
          logger.error(s"Received unexpected response status: $status from url: $url whilst retrieving from ETMP Display VAT Return with response body: ${response.body}")
          Left(UnexpectedResponseStatus(status, s"Unexpected response form Display VAT Return with status: $status and response body: ${response.body}"))
      }
  }
}
