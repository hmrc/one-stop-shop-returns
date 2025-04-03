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

import config.{EtmpDisplayVatReturnConfig, EtmpListObligationsConfig}
import connectors.EtmpListObligationsHttpParser.{EtmpListObligationsReads, EtmpListObligationsResponse}
import connectors.VatReturnHttpParser.{DisplayVatReturnResponse, EtmpVatReturnReads}
import models.etmp.EtmpObligationsQueryParameters
import models.{GatewayTimeout, Period}
import models.Period.toEtmpPeriodString
import play.api.Logging
import play.api.http.HeaderNames.AUTHORIZATION
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpException, StringContextOps}

import java.net.URL
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class VatReturnConnector @Inject()(
                                    httpClientV2: HttpClientV2,
                                    etmpListObligationsConfig: EtmpListObligationsConfig,
                                    etmpDisplayVatReturnConfig: EtmpDisplayVatReturnConfig
                                  )(implicit ec: ExecutionContext) extends Logging {

  private implicit val emptyHc: HeaderCarrier = HeaderCarrier()

  private def obligationsHeaders(correlationId: String): Seq[(String, String)] = etmpListObligationsConfig.headers(correlationId)

  private def etmpDisplayHeaders(correlationId: String): Seq[(String, String)] = etmpDisplayVatReturnConfig.headers(correlationId)

  private def getObligationsUrl(vrn: String): URL =
    url"${etmpListObligationsConfig.baseUrl}enterprise/obligation-data/${etmpListObligationsConfig.idType}/$vrn/${etmpListObligationsConfig.regimeType}"

  def getObligations(vrn: String, queryParameters: EtmpObligationsQueryParameters): Future[EtmpListObligationsResponse] = {

    val correlationId = UUID.randomUUID().toString
    val headersWithCorrelationId = obligationsHeaders(correlationId)

    val headersWithoutAuth = headersWithCorrelationId.filterNot {
      case (key, _) => key.matches(AUTHORIZATION)
    }

    val url = getObligationsUrl(vrn)

    logger.info(s"Sending getObligations request to ETMP with headers $headersWithoutAuth")

    httpClientV2.get(url)
      .setHeader(headersWithCorrelationId *)
      .transform(_.withQueryStringParameters(queryParameters.toSeqQueryParams *))
      .execute[EtmpListObligationsResponse].recover({
        case e: HttpException =>
          logger.error(s"Unexpected error response from ETMP $url, received status ${e.responseCode}, body of response was: ${e.message}")
          Left(GatewayTimeout)

      })
  }

  def get(vrn: Vrn, period: Period): Future[DisplayVatReturnResponse] = {

    val correlationId = UUID.randomUUID().toString
    val headersWithCorrelationId = etmpDisplayHeaders(correlationId)

    val headersWithoutAuth = headersWithCorrelationId.filterNot {
      case (key, _) => key.matches(AUTHORIZATION)
    }
    
    val url: URL = url"${etmpDisplayVatReturnConfig.baseUrl}/$vrn/${toEtmpPeriodString(period)}"

    logger.info(s"Sending get request to ETMP with headers $headersWithoutAuth")

    httpClientV2.get(url)
      .setHeader(headersWithCorrelationId *)
      .execute[DisplayVatReturnResponse]
      .recover {
        case e: HttpException =>
          logger.error(s"Unexpected error response from core $url, received status ${e.responseCode} with body: ${e.message}")
          Left(GatewayTimeout)
      }
  }
}
