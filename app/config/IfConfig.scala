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

package config

import play.api.Configuration

import java.time.format.DateTimeFormatter
import java.time.{Clock, LocalDateTime, ZoneId}
import java.util.Locale
import javax.inject.Inject
import play.api.http.HeaderNames._
import play.api.http.MimeTypes

class IfConfig @Inject()(config: Configuration, clock: Clock) {

  val baseUrl: Service = config.get[Service]("microservice.services.if")
  val authorizationToken: String = config.get[String]("microservice.services.if.authorizationToken")
  val environment: String = config.get[String]("microservice.services.if.environment")

  private val dateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss z")
    .withLocale(Locale.UK)
    .withZone(ZoneId.of("GMT"))

  private val XCorrelationId = "X-Correlation-Id"

  def ifHeaders(correlationId: String): Seq[(String, String)] = Seq(
    CONTENT_TYPE -> MimeTypes.JSON,
    ACCEPT -> MimeTypes.JSON,
    AUTHORIZATION -> s"Bearer $authorizationToken",
    DATE -> dateTimeFormatter.format(LocalDateTime.now(clock)),
    XCorrelationId -> correlationId,
    X_FORWARDED_HOST -> "MDTP"
  )
}
