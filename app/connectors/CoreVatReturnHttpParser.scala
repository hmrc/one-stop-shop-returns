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

package connectors

import com.fasterxml.jackson.databind.JsonMappingException
import logging.Logging
import models.core.{CoreErrorResponse, EisErrorResponse}
import play.api.http.Status._
import play.api.libs.json.JsSuccess
import uk.gov.hmrc.http.{HttpReads, HttpResponse}

import java.time.Instant

object CoreVatReturnHttpParser extends Logging {

  type CoreVatReturnResponse = Either[EisErrorResponse, Unit]

  implicit object CoreVatReturnReads extends HttpReads[CoreVatReturnResponse] {
    override def read(method: String, url: String, response: HttpResponse): CoreVatReturnResponse =
      response.status match {
        case ACCEPTED =>
          Right()
        case status =>
          logger.info(s"Response received from core vat returns ${response.status} with body ${response.body}")
          if(response.body.isEmpty) {
            Left(
              EisErrorResponse(
                CoreErrorResponse(Instant.now(), None, s"UNEXPECTED_$status", "The response body was empty")
              ))
          } else {
            response.json.validateOpt[EisErrorResponse] match {
              case JsSuccess(Some(value), _) =>
                logger.error(s"Error response from core $url, received status $status, body of response was: ${response.body}")
                Left(value)
              case _ =>
                logger.error(s"Unexpected error response from core $url, received status $status, body of response was: ${response.body}")
                Left(
                  EisErrorResponse(
                    CoreErrorResponse(Instant.now(), None, s"UNEXPECTED_$status", response.body)
                  ))
            }
          }
      }

  }

}
