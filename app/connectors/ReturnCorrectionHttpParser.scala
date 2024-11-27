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
import models.corrections.ReturnCorrectionValue
import models.{ErrorResponse, InvalidJson, UnexpectedResponseStatus}
import play.api.http.Status.OK
import play.api.libs.json.{JsError, JsSuccess}
import uk.gov.hmrc.http.{HttpReads, HttpResponse}

object ReturnCorrectionHttpParser extends Logging {

  type ReturnCorrectionResponse = Either[ErrorResponse, ReturnCorrectionValue]

  implicit object ReturnCorrectionReads extends HttpReads[ReturnCorrectionResponse] {

    override def read(method: String, url: String, response: HttpResponse): ReturnCorrectionResponse = {
      response.status match {
        case OK =>
          response.json.validate[ReturnCorrectionValue] match {
            case JsSuccess(value, _) => Right(value)
            case JsError(errors) =>
              logger.warn(s"Failed to parse JSON $url with errors: $errors")
              Left(InvalidJson)
          }

        case status =>
          logger.error(s"Unexpected response from Return Correction $url. Received status: $status with response body: ${response.body}")
          Left(UnexpectedResponseStatus(status, s"Unexpected response from Return Correction. Received status: $status with response body: ${response.body}"))
      }
    }
  }
}
