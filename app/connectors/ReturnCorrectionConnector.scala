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

import config.ReturnCorrectionConfig
import connectors.ReturnCorrectionHttpParser.{ReturnCorrectionReads, ReturnCorrectionResponse}
import models.Period
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.http.{HeaderCarrier, HttpErrorFunctions, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ReturnCorrectionConnector @Inject()(
                                           returnCorrectionConfig: ReturnCorrectionConfig,
                                           httpClientV2: HttpClientV2
                                         )(implicit ec: ExecutionContext) extends HttpErrorFunctions {

  private implicit lazy val emptyHc: HeaderCarrier = HeaderCarrier()
  private val baseUrl: String = returnCorrectionConfig.baseUrl.baseUrl

  private def headers(correlationId: String): Seq[(String, String)] = returnCorrectionConfig.ifHeaders(correlationId)

  def getMaximumCorrectionValue(vrn: Vrn, countryCode: String, period: String): Future[ReturnCorrectionResponse] = {

    val correlationId: String = UUID.randomUUID().toString
    val headersWithCorrelationId = headers(correlationId)

    httpClientV2.get(url"$baseUrl/$vrn/$countryCode/$period")
      .setHeader(headersWithCorrelationId: _*)
      .execute[ReturnCorrectionResponse]
  }
}
