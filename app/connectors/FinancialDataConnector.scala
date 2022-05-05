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
import config.DesConfig
import connectors.FinancialDataHttpParser._
import logging.Logging
import models.des.UnexpectedResponseStatus
import models.financialdata.FinancialDataQueryParameters
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpException}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class FinancialDataConnector @Inject() (
                                         http:      HttpClient,
                                         desConfig: DesConfig
                                       )(implicit ec: ExecutionContext) extends Logging {

  private implicit val emptyHc: HeaderCarrier = HeaderCarrier()
  private val headers: Seq[(String, String)] = desConfig.desHeaders

  private def financialDataUrl(vrn: Vrn) =
    s"${desConfig.baseUrl}enterprise/financial-data/${vrn.name.toUpperCase()}/${vrn.value}/${desConfig.regimeType}"

  def getFinancialData(vrn: Vrn, queryParameters: FinancialDataQueryParameters): Future[FinancialDataResponse] = {
    val url = financialDataUrl(vrn)
    http.GET[FinancialDataResponse](
      url,
      queryParameters.toSeqQueryParams,
      headers = headers
    ).recover {
      case e: HttpException =>
        logger.error(s"Unexpected error response getting financial data from $url, received status ${e.responseCode}, body of response was: ${e.message}")
        Left(
          UnexpectedResponseStatus(e.responseCode, s"Unexpected response from DES, received status ${e.responseCode}")
        )
    }
  }
}