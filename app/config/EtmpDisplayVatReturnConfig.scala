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

package config

import play.api.Configuration
import play.api.http.HeaderNames.*
import play.api.http.MimeTypes
import utils.Formatters

import java.time.{Clock, LocalDateTime, ZoneOffset}
import javax.inject.Inject

class EtmpDisplayVatReturnConfig @Inject()(config: Configuration, clock: Clock) {

  val baseUrl: Service = config.get[Service]("microservice.services.display-vat-return")
  val authorizationToken: String = config.get[String]("microservice.services.display-vat-return.authorizationToken")
  val environment: String = config.get[String]("microservice.services.display-vat-return.environment")

  private val XCorrelationId = "X-Correlation-Id"

  def headers(correlationId: String): Seq[(String, String)] = Seq(
    CONTENT_TYPE -> MimeTypes.JSON,
    ACCEPT -> MimeTypes.JSON,
    AUTHORIZATION -> s"Bearer $authorizationToken",
    DATE -> Formatters.eisDateTimeFormatter.format(LocalDateTime.now(clock).atOffset(ZoneOffset.UTC)),
    XCorrelationId -> correlationId,
    X_FORWARDED_HOST -> "MDTP"
  )
}
