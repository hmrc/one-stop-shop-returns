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

import config.{EtmpDisplayVatReturnConfig, IfConfig}
import connectors.CoreVatReturnHttpParser._
import logging.Logging
import models.Period
import models.core.{CoreErrorResponse, CoreVatReturn, EisErrorResponse}
import models.responses.GatewayTimeout
import play.api.http.HeaderNames.AUTHORIZATION
import play.api.libs.json.Json
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpException, StringContextOps}
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue

import java.net.URL
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class CoreVatReturnConnector @Inject()(
                                        httpClientV2: HttpClientV2,
                                        ifConfig: IfConfig,
                                        etmpDisplayVatReturnConfig: EtmpDisplayVatReturnConfig
                                      )(implicit ec: ExecutionContext) extends Logging {

  private implicit val emptyHc: HeaderCarrier = HeaderCarrier()

  private def headers(correlationId: String): Seq[(String, String)] = ifConfig.ifHeaders(correlationId)

  private def etmpDisplayHeaders(correlationId: String): Seq[(String, String)] = etmpDisplayVatReturnConfig.headers(correlationId)

  def submit(coreVatReturn: CoreVatReturn): Future[CoreVatReturnResponse] = {
    val correlationId = UUID.randomUUID().toString
    val headersWithCorrelationId = headers(correlationId)

    val headersWithoutAuth = headersWithCorrelationId.filterNot {
      case (key, _) => key.matches(AUTHORIZATION)
    }

    val url: URL = url"${ifConfig.baseUrl}"

    logger.info(s"Sending request to core with headers $headersWithoutAuth")

    httpClientV2.post(url).withBody(Json.toJson(coreVatReturn)).setHeader(headersWithCorrelationId*).execute[CoreVatReturnResponse].recover {
      case e: HttpException =>
        logger.error(s"Unexpected error response from core $url, received status ${e.responseCode}, body of response was: ${e.message}")
        Left(
          EisErrorResponse(
            CoreErrorResponse(Instant.now(), None, s"UNEXPECTED_${e.responseCode}", e.message)
          ))
    }
  }

  def get(vrn: Vrn, period: Period): Future[DisplayVatReturnResponse] = {

    val correlationId = UUID.randomUUID().toString
    val headersWithCorrelationId = etmpDisplayHeaders(correlationId)

    val headersWithoutAuth = headersWithCorrelationId.filterNot {
      case (key, _) => key.matches(AUTHORIZATION)
    }

    val url: URL = url"${etmpDisplayVatReturnConfig.baseUrl}/$vrn/$period"

    logger.info(s"Sending get request to ETMP with headers $headersWithoutAuth")

    httpClientV2.get(url).setHeader(headersWithCorrelationId: _*).execute[DisplayVatReturnResponse].recover {
      case e: HttpException =>
        logger.error(s"Unexpected error response from core $url, received status ${e.responseCode} with body: ${e.message}")
        Left(GatewayTimeout)
    }
  }
}
