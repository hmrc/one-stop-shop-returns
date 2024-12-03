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

import models.{ErrorResponse, EtmpListObligationsError, ServerError}
import models.etmp.EtmpObligations
import play.api.Logging
import play.api.http.Status.OK
import play.api.libs.json.{JsError, JsSuccess}
import uk.gov.hmrc.http.{HttpReads, HttpResponse}


object EtmpListObligationsHttpParser extends Logging {

  type EtmpListObligationsResponse = Either[ErrorResponse, EtmpObligations]

  implicit object EtmpListObligationsReads extends HttpReads[EtmpListObligationsResponse] {

    override def read(method: String, url: String, response: HttpResponse): EtmpListObligationsResponse =
      response.status match {
        case OK =>
          response.json.validate[EtmpObligations] match {
            case JsSuccess(obligations, _) =>
              Right(obligations)
            case JsError(errors) =>
              logger.error(s"Error parsing JSON response from ETMP $errors")
              Left(ServerError)
          }
        case status =>
          logger.info(s"Response received from etmp obligations ${response.status} with body ${response.body}")
          if (response.body.isEmpty) {
            Left(
              EtmpListObligationsError(s"UNEXPECTED_$status", "The response body was empty")
            )
          } else {
            logger.error(s"Unexpected error response from core $url, received status $status, body of response was: ${response.body}")
            Left(
              EtmpListObligationsError(status.toString, response.body)
            )
          }
      }
  }
}
